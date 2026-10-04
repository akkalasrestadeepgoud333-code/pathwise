package app.pathwise.mobile;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Today: level, streak, the next steps Pathwise suggests (with their reasons), and the way into each area.
 * On open it checks for unread notifications and, if the student allowed notifications, shows one summary.
 */
public class MainActivity extends Screen {
    private static final String CHANNEL = "updates";
    private static final int ASK_NOTIFICATIONS = 7;

    @Override
    protected void load() {
        clear();
        heading("Today");
        // One after the other, so the progress card always sits above the suggestions.
        Api.get(this, "/api/v1/me/rewards", r -> {
            JSONObject s = r.getJSONObject("summary");
            card("Your progress", "Level " + s.getInt("level") + " · " + s.getLong("xp") + " XP",
                    s.getInt("streak") + "-day streak" + (s.getBoolean("activeToday") ? " · today counts" : " · learn something today to keep it"),
                    null);
            recommendations();
        }, (status, m) -> {
            error(m);
            recommendations();
        });
        checkNotifications();
    }

    private void recommendations() {
        Api.get(this, "/api/v1/me/recommendations", r -> {
            JSONArray items = r.getJSONArray("items");
            if (items.length() == 0) {
                note("No suggestions yet. Start with Practice or a game.");
            }
            for (int i = 0; i < Math.min(items.length(), 4); i++) {
                JSONObject rec = items.getJSONObject(i);
                card(rec.optString("slotLabel"), rec.getString("title"), rec.optString("reasonText"), null);
            }
            menu();
        }, (status, m) -> {
            error(m);
            menu();
        });
    }

    private void menu() {
        button("Practice", v -> open(PracticeActivity.class, null, null));
        button("Games", v -> open(GamesActivity.class, null, null));
        button("Rooms", v -> open(RoomsActivity.class, null, null));
        button("Doubts", v -> open(DoubtsActivity.class, null, null));
        button("Notifications", v -> open(NotificationsActivity.class, null, null));
        button("Sign out", v -> {
            Api.signOut(this);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    /** Asks once, explaining why, and respects "no". Android 13+ requires this permission to show notifications. */
    private void checkNotifications() {
        boolean asked = Api.prefs(this).getBoolean("askedNotifications", false);
        if (Build.VERSION.SDK_INT >= 33 && !asked && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Api.prefs(this).edit().putBoolean("askedNotifications", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("Get updates?")
                    .setMessage("Pathwise can tell you when someone answers your doubt, a result is ready or you earn a badge. It never sends nagging reminders.")
                    .setPositiveButton("Continue", (d, w) -> requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, ASK_NOTIFICATIONS))
                    .setNegativeButton("Not now", null)
                    .show();
        }
        Api.get(this, "/api/v1/me/notifications", r -> {
            int unread = r.getInt("unread");
            int lastShown = Api.prefs(this).getInt("lastUnreadShown", 0);
            if (unread > 0 && unread != lastShown && canNotify()) {
                showSummary(unread, r.getJSONArray("items").getJSONObject(0).getString("title"));
            }
            Api.prefs(this).edit().putInt("lastUnreadShown", unread).apply();
        }, (status, m) -> { });
    }

    private boolean canNotify() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void showSummary(int unread, String latest) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Pathwise updates", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, NotificationsActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(unread == 1 ? latest : unread + " new updates")
                .setContentText(unread == 1 ? "Tap to open" : latest)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(1, n);
    }
}
