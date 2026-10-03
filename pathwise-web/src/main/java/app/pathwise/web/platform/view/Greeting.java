package app.pathwise.web.platform.view;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Time-of-day greeting in the learner's timezone. */
public final class Greeting {
    private Greeting() {}

    public static String forNow(ZoneId zone) {
        return forTime(ZonedDateTime.now(zone).toLocalTime());
    }

    static String forTime(LocalTime t) {
        int h = t.getHour();
        if (h >= 5 && h < 12) {
            return "Good morning";
        }
        if (h >= 12 && h < 17) {
            return "Good afternoon";
        }
        return "Good evening";
    }
}
