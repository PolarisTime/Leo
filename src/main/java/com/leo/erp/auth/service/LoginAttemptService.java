package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class LoginAttemptService {

    private static final String FAILURE_KEY_PREFIX = "auth:login:fail:";
    private static final String LOCK_KEY_PREFIX = "auth:login:lock:";
    private static final long SECONDS_PER_MINUTE = 60;

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final DefaultRedisScript<Long> recordFailureScript;

    public LoginAttemptService(StringRedisTemplate redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
        this.recordFailureScript = new DefaultRedisScript<>();
        this.recordFailureScript.setLocation(new ClassPathResource("db/login_attempt_record_failure.lua"));
        this.recordFailureScript.setResultType(Long.class);
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
        Long failureCount = redisTemplate.execute(
                recordFailureScript,
                java.util.List.of(failureKey(loginName), lockKey(loginName)),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(config.getFailureWindowSeconds()),
                String.valueOf(config.getMaxFailures()),
                String.valueOf(config.getLockDurationSeconds())
        );
        if (failureCount == null) {
            return;
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
        return (loginName == null ? "" : loginName.trim()).toLowerCase(Locale.ROOT);
    }

    private String formatWaitTime(long remainingSeconds) {
        if (remainingSeconds < SECONDS_PER_MINUTE) {
            return remainingSeconds + " 秒";
        }
        long minutes = remainingSeconds / SECONDS_PER_MINUTE;
        long seconds = remainingSeconds % SECONDS_PER_MINUTE;
        if (seconds == 0) {
            return minutes + " 分钟";
        }
        return minutes + " 分钟 " + seconds + " 秒";
    }
}
