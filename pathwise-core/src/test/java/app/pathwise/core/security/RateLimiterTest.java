package app.pathwise.core.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    };

    @Test
    void allowsCapacityThenBlocksUntilRefill() {
        RateLimiter limiter = new RateLimiter(5, 60_000, clock);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("login:a")).isTrue();
        }
        assertThat(limiter.tryAcquire("login:a")).isFalse();
        assertThat(limiter.retryAfterMillis("login:a")).isBetween(1L, 12_000L);

        now.addAndGet(12_000); // one token per 12 s
        assertThat(limiter.tryAcquire("login:a")).isTrue();
        assertThat(limiter.tryAcquire("login:a")).isFalse();
    }

    @Test
    void keysAreIndependentAndResettable() {
        RateLimiter limiter = new RateLimiter(1, 60_000, clock);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        limiter.reset("a");
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    @Test
    void evictsOnlyFullBuckets() {
        RateLimiter limiter = new RateLimiter(2, 1_000, clock);
        limiter.tryAcquire("x");
        limiter.evictIdle();
        assertThat(limiter.retryAfterMillis("x")).isZero(); // still tracked, one token left
        now.addAndGet(5_000);
        limiter.evictIdle();
        assertThat(limiter.tryAcquire("x")).isTrue();
    }
}
