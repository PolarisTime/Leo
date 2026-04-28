package com.leo.erp.common.idempotent;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class IdempotentKeyService {

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:";
    private static final String PENDING_MARKER = "__PENDING__";

    private final StringRedisTemplate redisTemplate;

    public IdempotentKeyService(@Nullable StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String key, Duration ttl) {
        if (redisTemplate == null) {
            return true;
        }
        String redisKey = IDEMPOTENT_KEY_PREFIX + key;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, PENDING_MARKER, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void markCompleted(String key, String result, Duration ttl) {
        if (redisTemplate == null) {
            return;
        }
        redisTemplate.opsForValue().set(IDEMPOTENT_KEY_PREFIX + key, result, ttl);
    }

    public Optional<String> getResult(String key) {
        if (redisTemplate == null) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(IDEMPOTENT_KEY_PREFIX + key);
        if (value == null || PENDING_MARKER.equals(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public void release(String key) {
        if (redisTemplate == null) {
            return;
        }
        redisTemplate.delete(IDEMPOTENT_KEY_PREFIX + key);
    }

    public void throwIfDuplicate(String key) {
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请勿重复提交: " + key);
    }
}
