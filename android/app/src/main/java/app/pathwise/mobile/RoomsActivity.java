package app.pathwise.mobile;

import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Your rooms, and joining one with a code. */
public class RoomsActivity extends Screen {

    @Override
    protected void load() {
        Api.get(this, "/api/v1/rooms", r -> {
            clear();
            heading("Rooms");
            JSONArray learning = r.getJSONArray("learning");
            JSONArray pending = r.getJSONArray("pending");
            if (learning.length() == 0 && pending.length() == 0) {
                note("You're not in a room yet. Ask your teacher for the code.");
            }
            for (int i = 0; i < learning.length(); i++) {
                JSONObject room = learning.getJSONObject(i);
                String id = room.getString("id");
                card(room.getBoolean("archived") ? "Archived" : null, room.getString("name"),
                        room.getInt("students") + " students", v -> open(RoomActivity.class, "id", id));
            }
            for (int i = 0; i < pending.length(); i++) {
                card("Waiting for approval", pending.getJSONObject(i).getString("name"), null, null);
            }
            EditText code = input("Join code, e.g. K7M3-QX9P", false);
            button("Join", v -> {
                JSONObject body = new JSONObject();
                try {
                    body.put("code", code.getText().toString());
                } catch (JSONException ignored) {
                    return;
                }
                Api.post(this, "/api/v1/rooms/join", body, res -> {
                    if ("pending".equals(res.getString("status"))) {
                        note("Request sent to " + res.getString("roomName") + ". You'll get in once the teacher approves.");
                    } else {
                        open(RoomActivity.class, "id", res.getString("roomId"));
                    }
                }, (s, m) -> error(m));
            });
        }, (status, m) -> {
            clear();
            error(m);
        });
    }
}
