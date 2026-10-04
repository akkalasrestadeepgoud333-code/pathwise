package app.pathwise.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Plays a game in a WebView using the same JavaScript as the website (bundled in the APK). Pages are served
 * from the app's own assets under a private https origin; nothing else can load, and file access is off.
 * When the game ends, the action log goes to the server, which replays and verifies it.
 */
public class GameActivity extends Activity {
    static final String ORIGIN = "https://appassets.pathwise.local/";
    private WebView web;
    private String attemptId;
    private String puzzle = "{}";
    private String template = "";
    private long minGap = 300;
    private boolean finished;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Api.signedIn(this)) {
            finish();
            return;
        }
        setContentView(R.layout.activity_game);
        web = findViewById(R.id.web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setDomStorageEnabled(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith(ORIGIN)) {
                    return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", null, null);
                }
                String path = url.substring(ORIGIN.length()).replaceAll("[?#].*$", "");
                if (path.contains("..")) {
                    return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", null, null);
                }
                try {
                    return new WebResourceResponse(mime(path), "utf-8", getAssets().open(path));
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // the game page never navigates away
            }
        });
        web.addJavascriptInterface(new Bridge(), "PathwiseBridge");
        String key = getIntent().getStringExtra("key");
        setTitle("Loading…");
        Api.post(this, "/api/v1/games/" + key + "/start", null, started -> {
            attemptId = started.getString("attemptId");
            Api.get(this, "/api/v1/games/attempts/" + attemptId, view -> {
                template = view.getString("template");
                puzzle = view.getJSONObject("puzzle").toString();
                minGap = view.optLong("minGapMillis", 300);
                setTitle(view.getString("title"));
                web.loadUrl(ORIGIN + "game.html");
            }, this::fail);
        }, this::fail);
    }

    private void fail(int status, String message) {
        new AlertDialog.Builder(this).setTitle("Couldn't load the game").setMessage(message)
                .setPositiveButton("OK", (d, w) -> finish()).show();
    }

    static String mime(String path) {
        if (path.endsWith(".js") || path.endsWith(".mjs")) {
            return "text/javascript";
        }
        if (path.endsWith(".css")) {
            return "text/css";
        }
        if (path.endsWith(".html")) {
            return "text/html";
        }
        return "application/octet-stream";
    }

    /** What the page may ask of the app: the puzzle, and to submit the finished game. Nothing else. */
    final class Bridge {
        @JavascriptInterface
        public String puzzle() {
            return puzzle;
        }

        @JavascriptInterface
        public String template() {
            return template;
        }

        @JavascriptInterface
        public long minGap() {
            return minGap;
        }

        @JavascriptInterface
        public void finish(String actionsJson) {
            runOnUiThread(() -> submit(actionsJson));
        }
    }

    private void submit(String actionsJson) {
        if (finished || attemptId == null) {
            return;
        }
        finished = true;
        JSONObject body = new JSONObject();
        try {
            body.put("actions", new JSONArray(actionsJson));
        } catch (JSONException e) {
            fail(0, "The game log couldn't be read.");
            return;
        }
        Api.post(this, "/api/v1/games/attempts/" + attemptId + "/finish", body, result -> {
            boolean ok = "completed".equals(result.getString("status"));
            String message = ok
                    ? result.getInt("score") + " / " + result.getInt("maxScore") + " · " + result.getInt("accuracyPercent")
                    + "% right first time.\nThis counts as light practice, never as a grade."
                    : "We couldn't verify this game: " + result.optString("rejectReason", "") + " It doesn't count, but nothing else changes.";
            new AlertDialog.Builder(this).setTitle(ok ? "Verified" : "Not verified").setMessage(message)
                    .setPositiveButton("Done", (d, w) -> finish()).setCancelable(false).show();
        }, this::fail);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("PathwiseBridge");
            web.destroy();
        }
        super.onDestroy();
    }
}
