package app.pathwise.core.identity;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Server-side password rules. The client-side strength meter is only a hint; this is the authority. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    /** A short deny-list of passwords that pass the length rule but are guessed first by attackers. */
    private static final Set<String> COMMON = Set.of(
            "password12", "password123", "1234567890", "qwertyuiop", "iloveyou12",
            "letmein123", "welcome123", "admin12345", "pathwise123", "abcdefghij",
            "0987654321", "1111111111", "passw0rd12", "qwerty1234", "1q2w3e4r5t");

    private PasswordPolicy() {}

    /** Returns an error message, or empty when the password is acceptable. */
    public static Optional<String> check(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of("Use at least " + MIN_LENGTH + " characters.");
        }
        if (password.length() > MAX_LENGTH) {
            return Optional.of("Use at most " + MAX_LENGTH + " characters.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            return Optional.of("That password is too common. Try a short phrase instead.");
        }
        if (password.chars().distinct().count() < 4) {
            return Optional.of("Use a mix of different characters.");
        }
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
            if (local.length() >= 4 && lower.contains(local)) {
                return Optional.of("Don't include your email name in your password.");
            }
        }
        return Optional.empty();
    }
}
