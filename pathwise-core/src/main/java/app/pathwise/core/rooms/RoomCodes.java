package app.pathwise.core.rooms;

import java.security.SecureRandom;
import java.util.Locale;

/** Room join codes: 8 characters from an alphabet without look-alikes (no 0/O, 1/I/L), shown as XXXX-XXXX. */
public final class RoomCodes {
    public static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RoomCodes() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** For display: {@code K7M3-QX9P}. */
    public static String format(String code) {
        return code.length() == 8 ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }

    /** Normalises what a student typed (case, spaces and dashes ignored). Returns null if it can't be a code. */
    public static String normalise(String typed) {
        if (typed == null) {
            return null;
        }
        String s = typed.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (s.length() != 8) {
            return null;
        }
        for (char ch : s.toCharArray()) {
            if (ALPHABET.indexOf(ch) < 0) {
                return null;
            }
        }
        return s;
    }
}
