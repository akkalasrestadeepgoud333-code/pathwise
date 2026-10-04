package app.pathwise.mobile;

import java.net.URI;
import java.util.Locale;

/** Server address rules (plain Java, unit-tested). */
public final class ServerUrl {
    private ServerUrl() {}

    /** "192.168.1.5:8080/" → "http://192.168.1.5:8080"; returns null if it can't be a web address. */
    public static String normalise(String typed) {
        if (typed == null) {
            return null;
        }
        String s = typed.trim();
        if (s.isEmpty() || s.matches("(?i)^https?:/*$")) {
            return null;
        }
        if (!s.matches("(?i)^https?://.*")) {
            s = "http://" + s;
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            URI u = URI.create(s);
            if (u.getHost() == null || (u.getPath() != null && !u.getPath().isEmpty()) || u.getQuery() != null) {
                return null;
            }
            return u.getScheme().toLowerCase(Locale.ROOT) + "://" + u.getHost() + (u.getPort() == -1 ? "" : ":" + u.getPort());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Plain HTTP to something that isn't this device, the emulator host or a private network: warn the user. */
    public static boolean insecureRemote(String url) {
        if (url == null || url.startsWith("https://")) {
            return false;
        }
        String host = URI.create(url).getHost();
        if (host == null) {
            return false;
        }
        return !(host.equals("localhost") || host.equals("127.0.0.1") || host.equals("10.0.2.2") || host.startsWith("192.168.")
                || host.startsWith("10.") || host.matches("172\\.(1[6-9]|2\\d|3[01])\\..*") || host.endsWith(".local"));
    }
}
