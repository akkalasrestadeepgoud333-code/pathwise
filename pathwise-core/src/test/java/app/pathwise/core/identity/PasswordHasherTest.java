package app.pathwise.core.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {
    private final PasswordHasher fast = new PasswordHasher(1024, 1, 1);

    @Test
    void verifiesTheCorrectPasswordOnly() {
        String hash = fast.hash("correct horse battery".toCharArray());
        assertThat(fast.verify("correct horse battery".toCharArray(), hash)).isTrue();
        assertThat(fast.verify("correct horse batterY".toCharArray(), hash)).isFalse();
        assertThat(fast.verify("".toCharArray(), hash)).isFalse();
    }

    @Test
    void usesPhcFormatWithRandomSalt() {
        String a = fast.hash("same password here".toCharArray());
        String b = fast.hash("same password here".toCharArray());
        assertThat(a).startsWith("$argon2id$v=19$m=1024,t=1,p=1$");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void productionDefaultsMatchOwaspBaseline() {
        String hash = new PasswordHasher().hash("a long enough password".toCharArray());
        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
    }

    @Test
    void handlesUnicodePasswords() {
        String hash = fast.hash("पासवर्ड-सुरक्षित-123".toCharArray());
        assertThat(fast.verify("पासवर्ड-सुरक्षित-123".toCharArray(), hash)).isTrue();
    }

    @Test
    void malformedStoredHashesNeverVerifyAndNeverThrow() {
        assertThat(fast.verify("x".toCharArray(), null)).isFalse();
        assertThat(fast.verify("x".toCharArray(), "")).isFalse();
        assertThat(fast.verify("x".toCharArray(), "$argon2i$v=19$m=1024,t=1,p=1$AAAA$AAAA")).isFalse();
        assertThat(fast.verify("x".toCharArray(), "$argon2id$v=19$m=-1,t=1,p=1$AAAA$AAAA")).isFalse();
        assertThat(fast.verify("x".toCharArray(), "$argon2id$v=19$m=1024,t=1,p=1$***$***")).isFalse();
    }

    @Test
    void flagsWeakerHashesForRehash() {
        PasswordHasher strong = new PasswordHasher(2048, 2, 1);
        String weak = fast.hash("password for rehash".toCharArray());
        assertThat(strong.needsRehash(weak)).isTrue();
        assertThat(strong.needsRehash(strong.hash("password for rehash".toCharArray()))).isFalse();
        assertThat(strong.needsRehash("garbage")).isTrue();
    }
}
