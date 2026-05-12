package com.leo.erp.system.norule.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
public class PreallocatedBusinessNoService {

    private static final String KEY_PREFIX = "leo:business-no:preallocated:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public PreallocatedBusinessNoService(@Nullable StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void reserve(String moduleKey, long id, SecurityPrincipal principal) {
        if (redisTemplate == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "预分配雪花ID服务不可用");
        }
        String key = buildKey(moduleKey, id);
        String value = buildValue(principal);
        Boolean created = redisTemplate.opsForValue().setIfAbsent(key, value, TTL);
        if (!Boolean.TRUE.equals(created)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "预分配雪花ID失败，请重试");
        }
    }

    public void consumeOrThrow(String moduleKey, long id, SecurityPrincipal principal) {
        if (redisTemplate == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "预分配雪花ID服务不可用");
        }
        String key = buildKey(moduleKey, id);
        String expectedValue = buildValue(principal);
        String actualValue = redisTemplate.opsForValue().get(key);
        if (!Objects.equals(expectedValue, actualValue)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "预分配雪花ID无效或不属于当前用户");
        }
        redisTemplate.delete(key);
    }

    private String buildKey(String moduleKey, long id) {
        return KEY_PREFIX + moduleKey.trim() + ":" + id;
    }

    private String buildValue(SecurityPrincipal principal) {
        if (principal == null || principal.id() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return String.valueOf(principal.id());
    }
}
