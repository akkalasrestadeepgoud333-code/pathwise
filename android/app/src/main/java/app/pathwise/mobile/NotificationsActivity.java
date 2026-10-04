package app.pathwise.mobile;

import org.json.JSONArray;
import org.json.JSONObject;

/** The same notifications as the website's bell; "Mark all as read" clears them everywhere. */
public class NotificationsActivity extends Screen {

    @Override
    protected void load() {
        Api.get(this, "/api/v1/me/notifications", r -> {
            clear();
            heading("Notifications");
            JSONArray items = r.getJSONArray("items");
            if (items.length() == 0) {
                note("Nothing new.");
                return;
            }
            button("Mark all as read", v -> Api.post(this, "/api/v1/me/notifications/read-all", null, res -> load(), (s, m) -> error(m)));
            for (int i = 0; i < items.length(); i++) {
                JSONObject n = items.getJSONObject(i);
                card((n.getBoolean("unread") ? "New · " : "") + n.getString("when"), n.getString("title"), n.optString("body", ""), null);
            }
        }, (status, m) -> {
            clear();
            error(m);
        });
    }
}
