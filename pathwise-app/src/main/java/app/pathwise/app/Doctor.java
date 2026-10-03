package app.pathwise.app;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Environment check. Required items fail the check; optional items only print a free install hint.
 * Never changes anything on the machine.
 */
public final class Doctor {
    private static int failures;

    private Doctor() {}

    public static boolean run(Path root) {
        failures = 0;
        System.out.println("Pathwise doctor\n");

        int java = Runtime.version().feature();
        report(java >= 21, true, "Java " + Runtime.version(), "Install a free JDK 21+ (e.g. Eclipse Temurin).");
        report(command("git", "--version") || Files.isRegularFile(Path.of("C:/Program Files/Git/cmd/git.exe")), false, "Git", "Install Git for Windows (free): winget install Git.Git");
        report(command("node", "--version"), false, "Node.js (runs the JavaScript parity tests)", "Install Node.js LTS (free).");
        report(Files.exists(root.resolve(LocalConfig.FILE)), false, "Local config " + LocalConfig.FILE,
                "Created automatically on first run of scripts\\setup.ps1 or serve.");
        report(Files.isDirectory(root.resolve("pathwise-web/target/classes")), false, "Web module built",
                "Run: .\\mvnw.cmd -q install -DskipTests");
        int port = Integer.parseInt(LocalConfig.get("pathwise.http.port", "8080"));
        boolean free = portFree(port);
        report(free, false, "Port " + port + (free ? " is free" : " is in use (Pathwise may already be running)"),
                "Stop the other program or run serve with --port 8081.");
        report(http("http://localhost:11434/api/version"), false, "Ollama (optional local AI tutor)",
                "AI stays off without it. Free install: https://ollama.com, then scripts\\ai-setup.ps1.");
        report(command("cloudflared", "--version"), false, "cloudflared (optional public demo URL)",
                "Free install: winget install Cloudflare.cloudflared");
        Path jbr = Path.of("C:/Program Files/Android/Android Studio/jbr");
        report(Files.isDirectory(jbr), false, "Android Studio JBR (needed only for the Android app)",
                "Install Android Studio (free) to build the APK.");

        System.out.println(failures == 0 ? "\nAll required checks passed." : "\n" + failures + " required check(s) failed.");
        return failures == 0;
    }

    private static void report(boolean ok, boolean required, String what, String hint) {
        String mark = ok ? "[ok]  " : (required ? "[FAIL]" : "[--]  ");
        System.out.println(mark + " " + what);
        if (!ok) {
            System.out.println("        " + hint);
            if (required) {
                failures++;
            }
        }
    }

    private static boolean command(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean portFree(int port) {
        try (ServerSocket s = new ServerSocket()) {
            s.bind(new InetSocketAddress("127.0.0.1", port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean http(String url) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            HttpResponse<Void> r = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).build(),
                    HttpResponse.BodyHandlers.discarding());
            return r.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
