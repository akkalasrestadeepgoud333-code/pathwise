package app.pathwise.core.security;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket limiter keyed by an arbitrary string (for example "login:" + ip + ":" + email).
 * Suitable for a single-process deployment, which is what Pathwise runs as.
 */
public final class RateLimiter {
    private final int capacity;
    private final double refillPerMilli;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        double tokens;
        long updatedAt;

        Bucket(double tokens, long updatedAt) {
            this.tokens = tokens;
            this.updatedAt = updatedAt;
        }
    }

    /** Allows {@code capacity} events per {@code windowMillis}, refilled continuously. */
    public RateLimiter(int capacity, long windowMillis, Clock clock) {
        if (capacity <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("capacity and window must be positive");
        }
        this.capacity = capacity;
        this.refillPerMilli = (double) capacity / windowMillis;
        this.clock = clock;
    }

    /** Consumes one token if available. */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        synchronized (bucket) {
            refill(bucket, now);
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return true;
            }
            return false;
        }
    }

    /** Milliseconds until the next token, for Retry-After headers. */
    public long retryAfterMillis(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return 0;
        }
        synchronized (bucket) {
            refill(bucket, clock.millis());
            return bucket.tokens >= 1 ? 0 : (long) Math.ceil((1 - bucket.tokens) / refillPerMilli);
        }
    }

    /** Clears a key (for example after a successful login). */
    public void reset(String key) {
        buckets.remove(key);
    }

    /** Drops full buckets so the map does not grow without bound. */
    public void evictIdle() {
        long now = clock.millis();
        buckets.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                refill(e.getValue(), now);
                return e.getValue().tokens >= capacity;
            }
        });
    }

    private void refill(Bucket bucket, long now) {
        long elapsed = Math.max(0, now - bucket.updatedAt);
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * refillPerMilli);
        bucket.updatedAt = now;
    }
}
