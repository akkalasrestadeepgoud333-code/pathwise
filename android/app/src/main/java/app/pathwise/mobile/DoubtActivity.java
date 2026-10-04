package app.pathwise.mobile;

import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One doubt with its answers; answering. People appear only as the server labels them (aliases stay aliases). */
public class DoubtActivity extends Screen {

    @Override
    protected void load() {
        String id = getIntent().getStringExtra("id");
        Api.get(this, "/api/v1/doubts/" + id, t -> {
            clear();
            card(t.getString("scopeLabel") + " · " + t.getString("statusLabel"), t.getString("title"),
                    html(t.getJSONObject("body").getString("html")), null);
            note(t.getJSONObject("asker").getString("label") + " · " + t.getString("when"));
            JSONArray posts = t.getJSONArray("posts");
            heading(posts.length() + (posts.length() == 1 ? " answer" : " answers"));
            for (int i = 0; i < posts.length(); i++) {
                JSONObject p = posts.getJSONObject(i);
                String tags = (p.getBoolean("accepted") ? "Accepted · " : "") + (p.getBoolean("verified") ? "Teacher verified · " : "")
                        + "Helpful " + p.getInt("votes");
                card(p.getJSONObject("who").getString("label") + " · " + p.getString("when"), tags,
                        html(p.getJSONObject("body").getString("html")), null);
            }
            if (t.getBoolean("canAnswer")) {
                EditText answer = input(t.getBoolean("mine") ? "Add detail or reply" : "Your answer", true);
                button("Post", v -> {
                    JSONObject b = new JSONObject();
                    try {
                        b.put("body", answer.getText().toString());
                    } catch (JSONException ignored) {
                        return;
                    }
                    Api.post(this, "/api/v1/doubts/" + id + "/answers", b, res -> load(), (s, m) -> error(m));
                });
            }
        }, (status, m) -> {
            clear();
            error(m);
        });
    }
}
