package com.leo.erp.security.permission;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

@Slf4j
@Service
public class TokenBucketService {

    private static final String KEY_PREFIX = "rate-limit:bucket:";
    private static final double DEFAULT_RATE = 100.0;
    private static final int DEFAULT_CAPACITY = 150;
    private static final int DEFAULT_MAXIMUM_SIZE = 10_000;
    private static final Duration DEFAULT_EXPIRE_AFTER_ACCESS = Duration.ofMinutes(10);

    private final LocalBucketStore buckets;

    @Autowired
    public TokenBucketService(
            @Value("${leo.rate-limit.local-cache.maximum-size:10000}") int maximumSize,
            @Value("${leo.rate-limit.local-cache.expire-after-access-ms:600000}") long expireAfterAccessMs
    ) {
        this(maximumSize, Duration.ofMillis(expireAfterAccessMs), System::nanoTime);
    }

    TokenBucketService() {
        this(DEFAULT_MAXIMUM_SIZE, DEFAULT_EXPIRE_AFTER_ACCESS, System::nanoTime);
    }

    TokenBucketService(int maximumSize, Duration expireAfterAccess, LongSupplier ticker) {
        this.buckets = new LocalBucketStore(maximumSize, expireAfterAccess, ticker);
    }

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

            Bucket bucket = buckets.getOrCreate(bucketKey, this::createBucket);
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

    private static final class LocalBucketStore {

        private final int maximumSize;
        private final long expireAfterAccessNanos;
        private final LongSupplier ticker;
        private final LinkedHashMap<BucketKey, BucketEntry> entries = new LinkedHashMap<>(16, 0.75f, true);

        private LocalBucketStore(int maximumSize, Duration expireAfterAccess, LongSupplier ticker) {
            if (maximumSize <= 0) {
                throw new IllegalArgumentException("Local token bucket cache maximum size must be positive");
            }
            if (expireAfterAccess == null || expireAfterAccess.isZero() || expireAfterAccess.isNegative()) {
                throw new IllegalArgumentException("Local token bucket cache expiration must be positive");
            }
            if (ticker == null) {
                throw new IllegalArgumentException("Local token bucket cache ticker is required");
            }
            this.maximumSize = maximumSize;
            this.expireAfterAccessNanos = expireAfterAccess.toNanos();
            this.ticker = ticker;
        }

        private synchronized Bucket getOrCreate(BucketKey key, Function<BucketKey, Bucket> creator) {
            long now = ticker.getAsLong();
            removeExpired(now);

            BucketEntry existing = entries.get(key);
            if (existing != null) {
                existing.lastAccessNanos = now;
                return existing.bucket;
            }

            while (entries.size() >= maximumSize) {
                Iterator<BucketKey> iterator = entries.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }

            Bucket created = creator.apply(key);
            entries.put(key, new BucketEntry(created, now));
            return created;
        }

        private void removeExpired(long now) {
            Iterator<BucketEntry> iterator = entries.values().iterator();
            while (iterator.hasNext()) {
                BucketEntry entry = iterator.next();
                long idleNanos = now - entry.lastAccessNanos;
                if (idleNanos < 0 || idleNanos < expireAfterAccessNanos) {
                    break;
                }
                iterator.remove();
            }
        }

        private static final class BucketEntry {

            private final Bucket bucket;
            private long lastAccessNanos;

            private BucketEntry(Bucket bucket, long lastAccessNanos) {
                this.bucket = bucket;
                this.lastAccessNanos = lastAccessNanos;
            }
        }
    }
}
