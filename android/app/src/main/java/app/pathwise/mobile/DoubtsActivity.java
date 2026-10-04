package app.pathwise.mobile;

import android.widget.CheckBox;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Your doubts, the open doubt network, and asking one (anonymous by default). */
public class DoubtsActivity extends Screen {

    @Override
    protected void load() {
        Api.get(this, "/api/v1/doubts", r -> {
            clear();
            heading("Doubts");
            note("Ask the network: you appear as an anonymous asker unless you choose to show your name.");
            EditText title = input("Your question (8+ characters)", false);
            EditText body = input("Details: what you tried, where you got stuck", true);
            CheckBox showName = new CheckBox(this);
            showName.setText("Show my name");
            content.addView(showName);
            button("Ask the network", v -> {
                JSONObject b = new JSONObject();
                try {
                    b.put("category", "concept").put("title", title.getText().toString()).put("body", body.getText().toString())
                            .put("showName", showName.isChecked());
                } catch (JSONException ignored) {
                    return;
                }
                Api.post(this, "/api/v1/doubts", b, res -> open(DoubtActivity.class, "id", res.getString("id")), (s, m) -> error(m));
            });
            list("Yours", r.getJSONArray("mine"));
            list("Doubt network", r.getJSONArray("network"));
        }, (status, m) -> {
            clear();
            error(m);
        });
    }

    private void list(String label, JSONArray items) throws JSONException {
        if (items.length() == 0) {
            return;
        }
        heading(label);
        for (int i = 0; i < items.length(); i++) {
            JSONObject t = items.getJSONObject(i);
            String id = t.getString("id");
            card(t.getString("statusLabel") + (t.getBoolean("verified") ? " · teacher verified" : ""), t.getString("title"),
                    t.getInt("answers") + " answers · " + t.getString("when"), v -> open(DoubtActivity.class, "id", id));
        }
    }
}
