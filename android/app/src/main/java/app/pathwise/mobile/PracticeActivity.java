package app.pathwise.mobile;

import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Quick practice: five questions at your level, graded by the server, with feedback after each. */
public class PracticeActivity extends Screen {
    private String attemptId;

    @Override
    protected void load() {
        if (attemptId != null) {
            showStep();
            return;
        }
        clear();
        heading("Practice");
        note("Starting a quick set…");
        JSONObject body = new JSONObject();
        try {
            body.put("mode", "quick");
        } catch (JSONException ignored) {
            // constant key
        }
        Api.post(this, "/api/v1/practice", body, r -> {
            attemptId = r.getString("attemptId");
            showStep();
        }, (status, m) -> {
            clear();
            heading("Practice");
            error(m);
        });
    }

    private void showStep() {
        Api.get(this, "/api/v1/practice/" + attemptId, view -> {
            clear();
            heading(view.optString("title", "Practice"));
            if (!view.isNull("summary")) {
                JSONObject s = view.getJSONObject("summary");
                card("Done", s.getInt("correct") + " of " + s.getInt("total") + " right",
                        "This was practice: it shapes your suggestions and is never a grade.", null);
                button("Another set", v -> {
                    attemptId = null;
                    load();
                });
                return;
            }
            JSONObject step = view.getJSONObject("step");
            int position = step.getInt("position");
            JSONObject q = step.getJSONObject("question");
            card("Question " + position + " of " + step.getInt("total"), "", html(q.getJSONObject("stem").getString("html")), null);
            String type = q.getString("type");
            JSONArray options = q.getJSONArray("options");
            List<CheckBox> boxes = new ArrayList<>();
            RadioGroup group = new RadioGroup(this);
            EditText text = null;
            if ("single_choice".equals(type)) {
                for (int i = 0; i < options.length(); i++) {
                    RadioButton rb = new RadioButton(this);
                    rb.setId(1000 + i);
                    rb.setText(html(options.getJSONObject(i).getJSONObject("label").getString("html")));
                    rb.setTag(options.getJSONObject(i).getString("id"));
                    rb.setMinHeight(dp(48));
                    group.addView(rb);
                }
                content.addView(group);
            } else if ("multi_choice".equals(type)) {
                note("Select all that apply.");
                for (int i = 0; i < options.length(); i++) {
                    CheckBox cb = new CheckBox(this);
                    cb.setText(html(options.getJSONObject(i).getJSONObject("label").getString("html")));
                    cb.setTag(options.getJSONObject(i).getString("id"));
                    cb.setMinHeight(dp(48));
                    boxes.add(cb);
                    content.addView(cb);
                }
            } else {
                text = input("Your answer", false);
            }
            EditText answerText = text;
            button("Check answer", v -> {
                JSONObject answer = new JSONObject();
                try {
                    JSONArray chosen = new JSONArray();
                    if (group.getCheckedRadioButtonId() != -1) {
                        chosen.put(group.findViewById(group.getCheckedRadioButtonId()).getTag());
                    }
                    for (CheckBox cb : boxes) {
                        if (cb.isChecked()) {
                            chosen.put(cb.getTag());
                        }
                    }
                    answer.put("options", chosen);
                    answer.put("text", answerText == null ? "" : answerText.getText().toString());
                } catch (JSONException ignored) {
                    return;
                }
                Api.post(this, "/api/v1/practice/" + attemptId + "/" + position + "/answer", answer, this::feedback, (s, m) -> error(m));
            });
        }, (status, m) -> error(m));
    }

    private void feedback(JSONObject result) throws JSONException {
        JSONObject f = result.getJSONObject("feedback");
        boolean correct = f.getBoolean("correct");
        StringBuilder why = new StringBuilder();
        JSONArray opts = f.optJSONArray("options");
        if (opts != null) {
            for (int i = 0; i < opts.length(); i++) {
                JSONObject o = opts.getJSONObject(i);
                if (o.getBoolean("correct") || o.getBoolean("chosen")) {
                    why.append(o.getBoolean("correct") ? "✓ " : "✗ ").append(html(o.getJSONObject("label").getString("html")));
                    if (!o.isNull("rationale")) {
                        why.append(" — ").append(html(o.getJSONObject("rationale").getString("html")));
                    }
                    why.append("\n");
                }
            }
        } else if (!f.isNull("expected") && !correct) {
            why.append("Expected: ").append(f.getString("expected")).append("\n");
        }
        if (!f.isNull("explanation")) {
            why.append(html(f.getJSONObject("explanation").getString("html")));
        }
        clear();
        heading(correct ? "Correct" : f.getDouble("partial") > 0 ? "Partly right" : "Not quite");
        card(null, "Why", why.toString().trim(), null);
        button(result.getBoolean("last") ? "See how it went" : "Next question", v -> showStep());
    }
}
