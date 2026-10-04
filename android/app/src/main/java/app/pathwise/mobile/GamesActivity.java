package app.pathwise.mobile;

import org.json.JSONArray;
import org.json.JSONObject;

/** The games gallery. */
public class GamesActivity extends Screen {

    @Override
    protected void load() {
        Api.get(this, "/api/v1/games", r -> {
            clear();
            heading("Games");
            note("Short, untimed games. The server checks every finished game, and it counts as light practice, never a grade.");
            JSONArray items = r.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject g = items.getJSONObject(i);
                String key = g.getString("key");
                int plays = g.getInt("plays");
                card(g.getString("templateTitle"), g.getString("title"),
                        g.optString("description", "") + (plays > 0 ? "\nPlayed " + plays + "× · best " + g.optString("best") : ""),
                        v -> open(GameActivity.class, "key", key));
            }
        }, (status, m) -> {
            clear();
            error(m);
        });
    }
}
