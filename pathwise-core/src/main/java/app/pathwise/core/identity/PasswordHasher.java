package app.pathwise.core.identity;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Argon2id password hashing (OWASP baseline: m=19 MiB, t=2, p=1) encoded as a PHC string:
 * {@code $argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>}. Parameters live in the string, so they can be
 * raised later; {@link #needsRehash(String)} tells the login flow to upgrade old hashes.
 */
public final class PasswordHasher {
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final Base64.Encoder B64 = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getDecoder();

    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher() {
        this(19_456, 2, 1);
    }

    /** Lower costs are allowed only so tests stay fast; production uses the default constructor. */
    public PasswordHasher(int memoryKiB, int iterations, int parallelism) {
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    public String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, memoryKiB, iterations, parallelism, HASH_BYTES);
        return "$argon2id$v=19$m=" + memoryKiB + ",t=" + iterations + ",p=" + parallelism
                + "$" + B64.encodeToString(salt) + "$" + B64.encodeToString(hash);
    }

    /** Constant-time verification. Returns false (never throws) for malformed stored hashes. */
    public boolean verify(char[] password, String encoded) {
        Parsed p = parse(encoded);
        if (p == null) {
            return false;
        }
        byte[] actual = derive(password, p.salt, p.memory, p.iterations, p.parallelism, p.hash.length);
        return MessageDigest.isEqual(actual, p.hash);
    }

    public boolean needsRehash(String encoded) {
        Parsed p = parse(encoded);
        return p == null || p.memory < memoryKiB || p.iterations < iterations || p.parallelism != parallelism;
    }

    private static byte[] derive(char[] password, byte[] salt, int memory, int iterations, int parallelism, int length) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memory)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] out = new byte[length];
        byte[] pw = toUtf8(password);
        try {
            generator.generateBytes(pw, out);
        } finally {
            java.util.Arrays.fill(pw, (byte) 0);
        }
        return out;
    }

    private static byte[] toUtf8(char[] chars) {
        java.nio.ByteBuffer buf = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(chars));
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        if (buf.hasArray()) {
            java.util.Arrays.fill(buf.array(), (byte) 0);
        }
        return bytes;
    }

    private record Parsed(int memory, int iterations, int parallelism, byte[] salt, byte[] hash) {}

    private static Parsed parse(String encoded) {
        if (encoded == null) {
            return null;
        }
        String[] parts = encoded.split("\\$");
        // ["", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash]
        if (parts.length != 6 || !"argon2id".equals(parts[1]) || !"v=19".equals(parts[2])) {
            return null;
        }
        try {
            int m = 0, t = 0, p = 0;
            for (String kv : parts[3].split(",")) {
                String[] pair = kv.split("=", 2);
                int value = Integer.parseInt(pair[1]);
                switch (pair[0]) {
                    case "m" -> m = value;
                    case "t" -> t = value;
                    case "p" -> p = value;
                    default -> { return null; }
                }
            }
            if (m <= 0 || t <= 0 || p <= 0) {
                return null;
            }
            return new Parsed(m, t, p, B64D.decode(parts[4]), B64D.decode(parts[5]));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
