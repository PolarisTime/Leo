package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class LoginAttemptService {

    private static final String FAILURE_KEY_PREFIX = "auth:login:fail:";
    private static final String LOCK_KEY_PREFIX = "auth:login:lock:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    public LoginAttemptService(StringRedisTemplate redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
    }

    public void ensureLoginAllowed(String loginName) {
        if (!authProperties.getLoginProtection().isEnabled()) {
            return;
        }

        String lockKey = lockKey(loginName);
        Boolean locked = redisTemplate.hasKey(lockKey);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        long remainingSeconds = Math.max(1L, redisTemplate.getExpire(lockKey, TimeUnit.SECONDS));
        throw new BusinessException(
                ErrorCode.FORBIDDEN,
                "登录失败次数过多，请在 " + formatWaitTime(remainingSeconds) + " 后重试"
        );
    }

    public void recordFailure(String loginName) {
        if (!authProperties.getLoginProtection().isEnabled()) {
            return;
        }

        AuthProperties.LoginProtection config = authProperties.getLoginProtection();
        String failureKey = failureKey(loginName);
        Long failureCount = redisTemplate.opsForValue().increment(failureKey);
        if (failureCount == null) {
            return;
        }
        if (failureCount == 1L) {
            redisTemplate.expire(failureKey, Duration.ofSeconds(config.getFailureWindowSeconds()));
        }
        if (failureCount >= config.getMaxFailures()) {
            redisTemplate.opsForValue().set(
                    lockKey(loginName),
                    String.valueOf(System.currentTimeMillis()),
                    Duration.ofSeconds(config.getLockDurationSeconds())
            );
            redisTemplate.delete(failureKey);
        }
    }

    public void clearFailures(String loginName) {
        if (!authProperties.getLoginProtection().isEnabled()) {
            return;
        }
        redisTemplate.delete(failureKey(loginName));
        redisTemplate.delete(lockKey(loginName));
    }

    private String failureKey(String loginName) {
        return FAILURE_KEY_PREFIX + normalize(loginName);
    }

    private String lockKey(String loginName) {
        return LOCK_KEY_PREFIX + normalize(loginName);
    }

    private String normalize(String loginName) {
        return String.valueOf(loginName == null ? "" : loginName.trim()).toLowerCase(Locale.ROOT);
    }

    private String formatWaitTime(long remainingSeconds) {
        if (remainingSeconds < 60) {
            return remainingSeconds + " 秒";
        }
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        if (seconds == 0) {
            return minutes + " 分钟";
        }
        return minutes + " 分钟 " + seconds + " 秒";
    }
}
