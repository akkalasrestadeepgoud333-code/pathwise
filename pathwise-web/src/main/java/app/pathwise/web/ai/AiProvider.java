package app.pathwise.web.ai;

import app.pathwise.core.ai.Prompts.Message;
import app.pathwise.web.platform.AppConfig;
import app.pathwise.web.platform.Json;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A chat model. {@link #chat} returns empty when no model is available or the call fails; the tutor then
 * answers with Guided Help (Pathwise's own content), so students are never left without a reply.
 */
public interface AiProvider {

    Optional<String> chat(List<Message> messages);

    /** Shown to students so they always know what is answering them. */
    String label();

    boolean enabled();

    static AiProvider from(AppConfig.Ai config) {
        return config.enabled() ? new Ollama(config) : new None();
    }

    /** AI off: the tutor uses Guided Help only. */
    final class None implements AiProvider {
        @Override
        public Optional<String> chat(List<Message> messages) {
            return Optional.empty();
        }

        @Override
        public String label() {
            return "Guided Help (AI off)";
        }

        @Override
        public boolean enabled() {
            return false;
        }
    }

    /** A local Ollama server (free; nothing leaves the machine). Non-streaming {@code /api/chat}. */
    final class Ollama implements AiProvider {
        private static final Logger log = LoggerFactory.getLogger(Ollama.class);
        private final AppConfig.Ai config;
        private final HttpClient http;

        public Ollama(AppConfig.Ai config) {
            this.config = config;
            this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        }

        @Override
        public Optional<String> chat(List<Message> messages) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.model());
            body.put("stream", false);
            body.put("messages", messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList());
            body.put("options", Map.of("temperature", 0.3, "num_predict", 400));
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(config.url().replaceAll("/+$", "") + "/api/chat"))
                        .timeout(config.timeout()).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    log.warn("AI provider answered HTTP {}", res.statusCode());
                    return Optional.empty();
                }
                JsonNode json = Json.mapper().readTree(res.body());
                String content = json.path("message").path("content").asText("").strip();
                return content.isEmpty() ? Optional.empty() : Optional.of(content);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("AI provider unavailable ({}); using Guided Help", e.toString());
                return Optional.empty();
            }
        }

        @Override
        public String label() {
            return "AI Tutor · local model (" + config.model() + ")";
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }
}
