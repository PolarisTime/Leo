package com.leo.erp.system.norule.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.BusinessPreallocationService;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
public class PreallocatedBusinessNoService implements BusinessPreallocationService {

    private static final String KEY_PREFIX = "leo:business-no:preallocated:";
    private static final String VALUE_KEY_PREFIX = "leo:business-no:value:preallocated:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public PreallocatedBusinessNoService(@Nullable StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void reserve(String moduleKey, long id, SecurityPrincipal principal) {
        reserveValue(buildKey(moduleKey, id), principal, "预分配雪花ID失败，请重试");
    }

    public void reserveBusinessNo(String moduleKey, String businessNo, SecurityPrincipal principal) {
        reserveValue(buildValueKey(moduleKey, businessNo), principal, "预分配业务单号失败，请重试");
    }

    private void reserveValue(String key, SecurityPrincipal principal, String failureMessage) {
        if (redisTemplate == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "预分配雪花ID服务不可用");
        }
        String value = buildValue(principal);
        Boolean created = redisTemplate.opsForValue().setIfAbsent(key, value, TTL);
        if (!Boolean.TRUE.equals(created)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, failureMessage);
        }
    }

    public void assertReservedByPrincipal(String moduleKey, long id, SecurityPrincipal principal) {
        if (redisTemplate == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "预分配雪花ID服务不可用");
        }
        String key = buildKey(moduleKey, id);
        String expectedValue = buildValue(principal);
        String actualValue = redisTemplate.opsForValue().get(key);
        if (!Objects.equals(expectedValue, actualValue)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "预分配雪花ID无效或不属于当前用户");
        }
    }

    @Override
    public boolean isBusinessNoReservedByPrincipal(String moduleKey, String businessNo, SecurityPrincipal principal) {
        if (redisTemplate == null || businessNo == null || businessNo.isBlank()) {
            return false;
        }
        String actualValue = redisTemplate.opsForValue().get(buildValueKey(moduleKey, businessNo));
        return Objects.equals(buildValue(principal), actualValue);
    }

    public void consumeOrThrow(String moduleKey, long id, SecurityPrincipal principal) {
        assertReservedByPrincipal(moduleKey, id, principal);
        redisTemplate.delete(buildKey(moduleKey, id));
    }

    public void consume(String moduleKey, long id) {
        if (redisTemplate == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "预分配雪花ID服务不可用");
        }
        String key = buildKey(moduleKey, id);
        redisTemplate.delete(key);
    }

    @Override
    public void consumeBusinessNo(String moduleKey, String businessNo) {
        if (redisTemplate == null || businessNo == null || businessNo.isBlank()) {
            return;
        }
        redisTemplate.delete(buildValueKey(moduleKey, businessNo));
    }

    private String buildKey(String moduleKey, long id) {
        return KEY_PREFIX + moduleKey.trim() + ":" + id;
    }

    private String buildValueKey(String moduleKey, String businessNo) {
        return VALUE_KEY_PREFIX + moduleKey.trim() + ":" + businessNo.trim();
    }

    private String buildValue(SecurityPrincipal principal) {
        if (principal == null || principal.id() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return String.valueOf(principal.id());
    }
}
