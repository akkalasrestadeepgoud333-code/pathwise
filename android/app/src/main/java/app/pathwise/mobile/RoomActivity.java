package app.pathwise.mobile;

import org.json.JSONArray;
import org.json.JSONObject;

/** One room as a student sees it: announcements, the curriculum with your status, and your next step. */
public class RoomActivity extends Screen {

    @Override
    protected void load() {
        String id = getIntent().getStringExtra("id");
        Api.get(this, "/api/v1/rooms/" + id, r -> {
            clear();
            setTitle(r.getString("name"));
            heading(r.getString("name"));
            if (!r.isNull("next")) {
                JSONObject next = r.getJSONObject("next");
                card("Your next step", next.getString("title"), next.getString("kindLabel") + " · " + next.getString("statusLabel")
                        + "\nOpen it on the website to continue.", null);
            }
            if (r.getInt("total") > 0) {
                note(r.getInt("done") + " of " + r.getInt("total") + " required items done");
            }
            JSONArray ann = r.getJSONArray("announcements");
            for (int i = 0; i < ann.length(); i++) {
                JSONObject a = ann.getJSONObject(i);
                card((a.getBoolean("pinned") ? "Pinned · " : "") + a.getString("author") + " · " + a.getString("when"),
                        "Announcement", a.getString("body"), null);
            }
            JSONArray units = r.getJSONArray("units");
            for (int u = 0; u < units.length(); u++) {
                JSONObject unit = units.getJSONObject(u);
                StringBuilder sb = new StringBuilder();
                JSONArray items = unit.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    sb.append("done".equals(it.getString("status")) ? "✓ " : "• ").append(it.getString("title")).append(" — ")
                            .append(it.getString("kindLabel")).append(", ").append(it.getString("statusLabel")).append('\n');
                }
                card("Unit", unit.getString("title"), sb.toString().trim(), null);
            }
        }, (status, m) -> {
            clear();
            error(m);
        });
    }
}
