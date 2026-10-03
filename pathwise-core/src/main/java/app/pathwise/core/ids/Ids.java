package app.pathwise.core.ids;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 generator (RFC 9562): 48-bit Unix-millisecond timestamp followed by random bits.
 * Time-ordered IDs keep B-tree indexes compact and are safe to expose in URLs.
 * Within one JVM, IDs are strictly increasing even when several are created in the same millisecond.
 */
public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Object LOCK = new Object();
    private static long lastMillis = -1;
    private static long sequence; // 12-bit counter stored in rand_a

    private Ids() {}

    public static UUID newId() {
        long millis;
        long seq;
        synchronized (LOCK) {
            millis = System.currentTimeMillis();
            if (millis <= lastMillis) {
                millis = lastMillis;
                sequence++;
                if (sequence > 0xFFF) { // counter exhausted: borrow the next millisecond
                    millis++;
                    sequence = 0;
                }
            } else {
                sequence = RANDOM.nextInt(0x400); // random start leaves headroom for the counter
            }
            lastMillis = millis;
            seq = sequence;
        }
        long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16;
        msb |= 0x7000L;          // version 7
        msb |= seq & 0xFFFL;     // rand_a used as monotonic counter
        long lsb = RANDOM.nextLong();
        lsb &= 0x3FFF_FFFF_FFFF_FFFFL;
        lsb |= 0x8000_0000_0000_0000L; // IETF variant
        return new UUID(msb, lsb);
    }

    /** Parses a UUID from user input, returning null instead of throwing for malformed values. */
    public static UUID parseOrNull(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
