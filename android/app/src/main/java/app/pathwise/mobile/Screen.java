package app.pathwise.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Base for the list-style screens: a scrolling column of cards, headings, inputs and buttons. */
public abstract class Screen extends Activity {
    protected LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Api.signedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_list);
        content = findViewById(R.id.content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) {
            load();
        }
    }

    /** (Re)loads the screen's data and rebuilds the column. */
    protected abstract void load();

    protected void clear() {
        content.removeAllViews();
    }

    protected TextView heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextAppearance(R.style.Title);
        t.setTextSize(22);
        t.setPadding(0, dp(8), 0, dp(12));
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            t.setAccessibilityHeading(true);
        }
        content.addView(t);
        return t;
    }

    protected TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextAppearance(R.style.Muted);
        t.setPadding(0, 0, 0, dp(12));
        content.addView(t);
        return t;
    }

    protected void error(String message) {
        TextView t = note(message);
        t.setTextColor(getColor(R.color.danger));
    }

    protected View card(String eyebrow, String title, CharSequence body, View.OnClickListener onClick) {
        View v = getLayoutInflater().inflate(R.layout.item_card, content, false);
        TextView e = v.findViewById(R.id.eyebrow);
        if (eyebrow != null && !eyebrow.isEmpty()) {
            e.setText(eyebrow);
            e.setVisibility(View.VISIBLE);
        }
        ((TextView) v.findViewById(R.id.title)).setText(title);
        TextView b = v.findViewById(R.id.body);
        if (body != null && body.length() > 0) {
            b.setText(body);
            b.setVisibility(View.VISIBLE);
            b.setMovementMethod(LinkMovementMethod.getInstance());
        }
        if (onClick != null) {
            v.setOnClickListener(onClick);
            v.setClickable(true);
            v.setFocusable(true);
        }
        content.addView(v);
        return v;
    }

    protected Button button(String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(onClick);
        content.addView(b);
        return b;
    }

    protected EditText input(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setContentDescription(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0) | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (multiline) {
            e.setMinLines(3);
        }
        content.addView(e);
        return e;
    }

    /** Server HTML (already sanitised by the server) for a TextView. */
    protected static Spanned html(String html) {
        return Html.fromHtml(html == null ? "" : html, Html.FROM_HTML_MODE_COMPACT);
    }

    protected int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    protected void open(Class<?> screen, String key, String value) {
        Intent i = new Intent(this, screen);
        if (key != null) {
            i.putExtra(key, value);
        }
        startActivity(i);
    }
}
