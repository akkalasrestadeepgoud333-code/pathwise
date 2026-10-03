package app.pathwise.web.platform.view;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Human-friendly times for views: "3 Oct 2026, 11:16 pm" and "5 min ago". */
public final class TimeLabels {
    /** Default until learner_profiles.timezone is user-editable. */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private TimeLabels() {}

    public static String dateTime(Instant at, ZoneId zone) {
        return at == null ? "" : DATE_TIME.format(at.atZone(zone)).replace("AM", "am").replace("PM", "pm");
    }

    public static String date(Instant at, ZoneId zone) {
        return at == null ? "" : DATE.format(at.atZone(zone));
    }

    public static String relative(Instant at, Instant now, ZoneId zone) {
        if (at == null) {
            return "";
        }
        Duration d = Duration.between(at, now);
        if (d.isNegative() || d.toSeconds() < 60) {
            return "just now";
        }
        if (d.toMinutes() < 60) {
            return d.toMinutes() + " min ago";
        }
        if (d.toHours() < 24) {
            long h = d.toHours();
            return h + (h == 1 ? " hour ago" : " hours ago");
        }
        if (d.toDays() < 7) {
            long days = d.toDays();
            return days == 1 ? "yesterday" : days + " days ago";
        }
        return date(at, zone);
    }
}
