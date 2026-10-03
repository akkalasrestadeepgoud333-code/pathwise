package app.pathwise.core.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void acceptsAReasonablePassphrase() {
        assertThat(PasswordPolicy.check("trees grow upside down", "asha@example.com")).isEmpty();
    }

    @Test
    void rejectsShortLongCommonRepetitiveAndEmailBasedPasswords() {
        assertThat(PasswordPolicy.check(null, null)).isPresent();
        assertThat(PasswordPolicy.check("short", null)).hasValueSatisfying(m -> assertThat(m).contains("10"));
        assertThat(PasswordPolicy.check("a".repeat(129), null)).isPresent();
        assertThat(PasswordPolicy.check("Password123", null)).isPresent();
        assertThat(PasswordPolicy.check("ababababababab", null)).isPresent();
        assertThat(PasswordPolicy.check("ashwini-2026-x", "ashwini@example.com")).isPresent();
    }
}
