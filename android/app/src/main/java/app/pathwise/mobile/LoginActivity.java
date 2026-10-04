package app.pathwise.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

/** Sign in or create an account on a Pathwise server (same accounts as the website). */
public class LoginActivity extends Activity {
    private boolean registering;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Api.signedIn(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_login);
        EditText server = findViewById(R.id.server);
        EditText name = findViewById(R.id.name);
        EditText email = findViewById(R.id.email);
        EditText password = findViewById(R.id.password);
        TextView error = findViewById(R.id.error);
        TextView warning = findViewById(R.id.warning);
        Button submit = findViewById(R.id.submit);
        Button toggle = findViewById(R.id.toggle);
        server.setText(Api.server(this));
        server.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String url = ServerUrl.normalise(s.toString());
                warning.setVisibility(url != null && ServerUrl.insecureRemote(url) ? View.VISIBLE : View.GONE);
            }
        });
        toggle.setOnClickListener(v -> {
            registering = !registering;
            int vis = registering ? View.VISIBLE : View.GONE;
            name.setVisibility(vis);
            findViewById(R.id.nameLabel).setVisibility(vis);
            submit.setText(registering ? "Create account" : "Sign in");
            toggle.setText(registering ? "Already have an account? Sign in" : "New here? Create an account");
        });
        submit.setOnClickListener(v -> {
            String url = ServerUrl.normalise(server.getText().toString());
            if (url == null) {
                show(error, "Enter the server address, like http://10.0.2.2:8080");
                return;
            }
            Api.prefs(this).edit().putString("server", url).apply();
            JSONObject body = new JSONObject();
            try {
                if (registering) {
                    body.put("displayName", name.getText().toString().trim());
                }
                body.put("email", email.getText().toString().trim());
                body.put("password", password.getText().toString());
            } catch (JSONException e) {
                return;
            }
            submit.setEnabled(false);
            error.setVisibility(View.GONE);
            Api.post(this, registering ? "/api/v1/auth/register" : "/api/v1/auth/login", body, res -> {
                Api.signIn(this, url, res.getString("token"));
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }, (status, message) -> {
                submit.setEnabled(true);
                show(error, message);
            });
        });
    }

    private static void show(TextView t, String message) {
        t.setText(message);
        t.setVisibility(View.VISIBLE);
    }
}
