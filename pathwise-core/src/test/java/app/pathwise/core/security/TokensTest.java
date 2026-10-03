package app.pathwise.core.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokensTest {

    @Test
    void tokensAreLongRandomAndUrlSafe() {
        String a = Tokens.newToken();
        String b = Tokens.newToken();
        assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void sha256IsStableHex() {
        assertThat(Tokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void constantTimeEqualsHandlesNulls() {
        assertThat(Tokens.constantTimeEquals("x", "x")).isTrue();
        assertThat(Tokens.constantTimeEquals("x", "y")).isFalse();
        assertThat(Tokens.constantTimeEquals(null, "y")).isFalse();
    }
}
