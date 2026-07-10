package com.leo.erp.security.permission;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketServiceTest {

    @Test
    void tryConsumeShouldReturnAllowedWhenTokensAvailable() {
        TokenBucketService service = service();

        TokenBucketService.TokenBucketResult result = service.tryConsume("test-key", 1);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isBetween(0L, 149L);
        assertThat(result.retryAfterMs()).isZero();
    }

    @Test
    void tryConsumeShouldDenyWhenLocalBucketIsEmpty() {
        TokenBucketService service = service();

        TokenBucketService.TokenBucketResult first = service.tryConsume("tiny", 1.0, 2, 1);
        TokenBucketService.TokenBucketResult second = service.tryConsume("tiny", 1.0, 2, 1);
        TokenBucketService.TokenBucketResult third = service.tryConsume("tiny", 1.0, 2, 1);

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(third.allowed()).isFalse();
        assertThat(third.remaining()).isZero();
        assertThat(third.retryAfterMs()).isGreaterThan(0);
    }

    @Test
    void tryConsumeShouldKeepBucketsSeparatedByDimension() {
        TokenBucketService service = service();

        service.tryConsume("left", 1.0, 1, 1);
        TokenBucketService.TokenBucketResult left = service.tryConsume("left", 1.0, 1, 1);
        TokenBucketService.TokenBucketResult right = service.tryConsume("right", 1.0, 1, 1);

        assertThat(left.allowed()).isFalse();
        assertThat(right.allowed()).isTrue();
    }

    @Test
    void tryConsumeShouldFailOpenForInvalidRequest() {
        TokenBucketService service = service();

        TokenBucketService.TokenBucketResult result = service.tryConsume("test-key", 1.0, 1, 0);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(1L);
    }

    @Test
    void retryAfterSecondsShouldRoundUp() {
        TokenBucketService.TokenBucketResult result = new TokenBucketService.TokenBucketResult(false, 0, 1500);

        assertThat(result.retryAfterSeconds()).isEqualTo(2);
    }

    @Test
    void retryAfterSecondsShouldReturnAtLeast1() {
        TokenBucketService.TokenBucketResult result = new TokenBucketService.TokenBucketResult(false, 0, 0);

        assertThat(result.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void localBucketStoreShouldEvictLeastRecentlyAccessedEntryAtCapacity() {
        AtomicLong ticker = new AtomicLong();
        TokenBucketService service = new TokenBucketService(2, Duration.ofMinutes(10), ticker::get);

        service.tryConsume("oldest", 0.001, 1, 1);
        service.tryConsume("evicted", 0.001, 1, 1);
        service.tryConsume("oldest", 0.001, 1, 1);
        service.tryConsume("newest", 0.001, 1, 1);

        TokenBucketService.TokenBucketResult recreated = service.tryConsume("evicted", 0.001, 1, 1);

        assertThat(recreated.allowed()).isTrue();
    }

    @Test
    void localBucketStoreShouldExpireOnlyAfterConfiguredIdleTime() {
        AtomicLong ticker = new AtomicLong();
        TokenBucketService service = new TokenBucketService(10, Duration.ofSeconds(10), ticker::get);
        service.tryConsume("idle", 0.001, 1, 1);

        ticker.addAndGet(Duration.ofSeconds(9).toNanos());
        assertThat(service.tryConsume("idle", 0.001, 1, 1).allowed()).isFalse();
        ticker.addAndGet(Duration.ofSeconds(9).toNanos());
        assertThat(service.tryConsume("idle", 0.001, 1, 1).allowed()).isFalse();

        ticker.addAndGet(Duration.ofSeconds(11).toNanos());
        assertThat(service.tryConsume("idle", 0.001, 1, 1).allowed()).isTrue();
    }

    private TokenBucketService service() {
        return new TokenBucketService(10_000, Duration.ofMinutes(10), System::nanoTime);
    }
}
