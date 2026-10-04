package app.pathwise.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to the Pathwise server's /api/v1 with the signed-in session's bearer token. Calls run off the main
 * thread; callbacks come back on it. The server does all checking; the app only displays.
 */
public final class Api {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String PREFS = "pathwise";

    private Api() {}

    public interface Ok {
        void ok(JSONObject body) throws JSONException;
    }

    public interface Fail {
        void fail(int status, String message);
    }

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String server(Context c) {
        return prefs(c).getString("server", "http://10.0.2.2:8080");
    }

    public static boolean signedIn(Context c) {
        return prefs(c).getString("token", null) != null;
    }

    public static void signIn(Context c, String server, String token) {
        prefs(c).edit().putString("server", server).putString("token", token).apply();
    }

    public static void signOut(Context c) {
        prefs(c).edit().remove("token").apply();
    }

    public static void get(Context c, String path, Ok ok, Fail fail) {
        run(c, "GET", path, null, ok, fail);
    }

    public static void post(Context c, String path, JSONObject body, Ok ok, Fail fail) {
        run(c, "POST", path, body == null ? new JSONObject() : body, ok, fail);
    }

    private static void run(Context c, String method, String path, JSONObject body, Ok ok, Fail fail) {
        Context app = c.getApplicationContext();
        IO.execute(() -> {
            int status = 0;
            String message;
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(server(app) + path).openConnection();
                con.setConnectTimeout(10_000);
                con.setReadTimeout(30_000);
                con.setRequestMethod(method);
                con.setRequestProperty("Accept", "application/json");
                String token = prefs(app).getString("token", null);
                if (token != null) {
                    con.setRequestProperty("Authorization", "Bearer " + token);
                }
                if (body != null) {
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream out = con.getOutputStream()) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                status = con.getResponseCode();
                InputStream in = status < 400 ? con.getInputStream() : con.getErrorStream();
                String text = in == null ? "" : read(in);
                JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
                if (status < 400) {
                    MAIN.post(() -> {
                        try {
                            ok.ok(json);
                        } catch (JSONException e) {
                            fail.fail(0, "The server sent something this app version doesn't understand.");
                        }
                    });
                    return;
                }
                if (status == 401) {
                    signOut(app);
                }
                message = json.optJSONObject("error") != null ? json.getJSONObject("error").optString("message", "Something went wrong.")
                        : "Something went wrong (" + status + ").";
            } catch (IOException e) {
                message = "Can't reach the Pathwise server. Check the address and that it's running.";
            } catch (JSONException e) {
                message = "The server sent something this app version doesn't understand.";
            }
            int s = status;
            String m = message;
            MAIN.post(() -> fail.fail(s, m));
        });
    }

    private static String read(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
