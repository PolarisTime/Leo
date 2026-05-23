package com.leo.erp.security.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TokenBucketService {

    private static final String KEY_PREFIX = "rate-limit:bucket:";
    private static final double DEFAULT_RATE = 100.0;
    private static final int DEFAULT_CAPACITY = 150;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> script;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public TokenBucketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource("db/token_bucket.lua"));
        s.setResultType(List.class);
        this.script = s;
    }

    public TokenBucketResult tryConsume(String dimensionKey, int requested) {
        return tryConsume(dimensionKey, DEFAULT_RATE, DEFAULT_CAPACITY, requested);
    }

    @SuppressWarnings("unchecked")
    public TokenBucketResult tryConsume(String dimensionKey, double rate, int capacity, int requested) {
        String tokensKey = KEY_PREFIX + dimensionKey + ":tokens";
        String timestampKey = KEY_PREFIX + dimensionKey + ":ts";
        long now = System.currentTimeMillis();

        try {
            List<Long> result = redisTemplate.execute(
                    script,
                    List.of(tokensKey, timestampKey),
                    String.valueOf(rate),
                    String.valueOf(capacity),
                    String.valueOf(now),
                    String.valueOf(requested)
            );
            if (result == null || result.size() < 3) {
                log.warn("TokenBucket Lua returned unexpected: {}", result);
                return TokenBucketResult.ALLOW_FALLBACK;
            }
            return new TokenBucketResult(
                    result.get(0) == 1L, result.get(1), result.get(2));
        } catch (Exception e) {
            log.error("TokenBucket Redis call failed, failing open", e);
            return TokenBucketResult.ALLOW_FALLBACK;
        }
    }

    public record TokenBucketResult(boolean allowed, long remaining, long retryAfterMs) {
        static final TokenBucketResult ALLOW_FALLBACK = new TokenBucketResult(true, 1, 0);

        public long retryAfterSeconds() {
            return Math.max(1, (retryAfterMs + 999) / 1000);
        }
    }
}
