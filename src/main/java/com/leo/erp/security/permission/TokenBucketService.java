package com.leo.erp.security.permission;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenBucketService {

    private static final String KEY_PREFIX = "rate-limit:bucket:";
    private static final double DEFAULT_RATE = 100.0;
    private static final int DEFAULT_CAPACITY = 150;

    private final ConcurrentMap<BucketKey, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketResult tryConsume(String dimensionKey, int requested) {
        return tryConsume(dimensionKey, DEFAULT_RATE, DEFAULT_CAPACITY, requested);
    }

    public TokenBucketResult tryConsume(String dimensionKey, double rate, int capacity, int requested) {
        try {
            BucketKey bucketKey = BucketKey.of(dimensionKey, rate, capacity);
            if (bucketKey == null || requested <= 0) {
                log.warn("Invalid token bucket arguments, failing open: key={}, rate={}, capacity={}, requested={}",
                        dimensionKey, rate, capacity, requested);
                return TokenBucketResult.ALLOW_FALLBACK;
            }

            Bucket bucket = buckets.computeIfAbsent(bucketKey, this::createBucket);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(requested);
            if (probe.isConsumed()) {
                return new TokenBucketResult(true, probe.getRemainingTokens(), 0);
            }
            long retryAfterMs = TimeUnit.NANOSECONDS.toMillis(probe.getNanosToWaitForRefill());
            return new TokenBucketResult(false, probe.getRemainingTokens(), Math.max(1, retryAfterMs));
        } catch (Exception e) {
            log.error("TokenBucket evaluation failed, failing open", e);
            return TokenBucketResult.ALLOW_FALLBACK;
        }
    }

    private Bucket createBucket(BucketKey key) {
        RefillPlan refillPlan = RefillPlan.fromRate(key.rate());
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(key.capacity())
                        .refillGreedy(refillPlan.tokens(), refillPlan.period()))
                .build();
    }

    public record TokenBucketResult(boolean allowed, long remaining, long retryAfterMs) {
        static final TokenBucketResult ALLOW_FALLBACK = new TokenBucketResult(true, 1, 0);

        public long retryAfterSeconds() {
            return Math.max(1, (retryAfterMs + 999) / 1000);
        }
    }

    private record BucketKey(String key, double rate, int capacity) {

        static BucketKey of(String dimensionKey, double rate, int capacity) {
            if (dimensionKey == null || dimensionKey.isBlank() || rate <= 0 || capacity <= 0
                    || !Double.isFinite(rate)) {
                return null;
            }
            String normalizedRate = String.format(Locale.ROOT, "%.6f", rate);
            String key = KEY_PREFIX + dimensionKey.trim() + ":" + capacity + ":" + normalizedRate;
            return new BucketKey(key, rate, capacity);
        }
    }

    private record RefillPlan(long tokens, Duration period) {

        static RefillPlan fromRate(double rate) {
            if (rate >= 1.0) {
                return new RefillPlan(Math.max(1, Math.round(rate)), Duration.ofSeconds(1));
            }
            long periodMillis = Math.max(1, Math.round(1000.0 / rate));
            return new RefillPlan(1, Duration.ofMillis(periodMillis));
        }
    }
}
