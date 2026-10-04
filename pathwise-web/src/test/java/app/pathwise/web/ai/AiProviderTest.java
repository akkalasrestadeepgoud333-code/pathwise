package app.pathwise.web.ai;

import app.pathwise.core.ai.Prompts.Message;
import app.pathwise.web.platform.AppConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The Ollama provider against a stub server: request shape, success, and graceful failure. */
class AiProviderTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String start(int status, String body, AtomicReference<String> seen) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", ex -> {
            seen.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void sendsTheConversationAndReadsTheReply() throws IOException {
        AtomicReference<String> seen = new AtomicReference<>();
        String url = start(200, "{\"message\":{\"role\":\"assistant\",\"content\":\"  Think about the root.  \"}}", seen);
        AiProvider p = AiProvider.from(new AppConfig.Ai("ollama", "llama3.2:3b", url, Duration.ofSeconds(5)));
        assertThat(p.enabled()).isTrue();
        assertThat(p.label()).contains("local model").contains("llama3.2:3b");
        assertThat(p.chat(List.of(new Message("system", "s"), new Message("user", "q")))).contains("Think about the root.");
        assertThat(seen.get()).contains("\"model\":\"llama3.2:3b\"").contains("\"stream\":false").contains("\"role\":\"user\"");
    }

    @Test
    void failuresFallBackInsteadOfBreaking() throws IOException {
        String url = start(500, "oops", new AtomicReference<>());
        AiProvider p = AiProvider.from(new AppConfig.Ai("ollama", "m", url, Duration.ofSeconds(5)));
        assertThat(p.chat(List.of(new Message("user", "q")))).isEmpty();
        stop();
        server = null;
        AiProvider down = AiProvider.from(new AppConfig.Ai("ollama", "m", "http://127.0.0.1:9", Duration.ofSeconds(2)));
        assertThat(down.chat(List.of(new Message("user", "q")))).isEmpty();
        AiProvider off = AiProvider.from(new AppConfig.Ai("none", "m", "http://x", Duration.ofSeconds(1)));
        assertThat(off.enabled()).isFalse();
        assertThat(off.label()).isEqualTo("Guided Help (AI off)");
        assertThat(off.chat(List.of())).isEmpty();
    }
}
