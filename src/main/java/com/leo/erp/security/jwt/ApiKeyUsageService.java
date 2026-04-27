package com.leo.erp.security.jwt;

import com.leo.erp.auth.repository.ApiKeyRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class ApiKeyUsageService {

    private static final String API_KEY_USAGE_PREFIX = "api-key:usage:";
    private static final Duration WRITE_THROTTLE_WINDOW = Duration.ofMinutes(1);

    private final ApiKeyRepository apiKeyRepository;
    private final StringRedisTemplate redisTemplate;

    public ApiKeyUsageService(ApiKeyRepository apiKeyRepository, StringRedisTemplate redisTemplate) {
        this.apiKeyRepository = apiKeyRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void markUsed(Long apiKeyId) {
        if (apiKeyId == null) {
            return;
        }

        LocalDateTime usedAt = LocalDateTime.now();
        try {
            Boolean shouldPersist = redisTemplate.opsForValue().setIfAbsent(
                    usageKey(apiKeyId),
                    usedAt.toString(),
                    WRITE_THROTTLE_WINDOW
            );
            if (Boolean.TRUE.equals(shouldPersist)) {
                apiKeyRepository.updateLastUsedAt(apiKeyId, usedAt);
            }
        } catch (RuntimeException ex) {
            apiKeyRepository.updateLastUsedAt(apiKeyId, usedAt);
        }
    }

    private String usageKey(Long apiKeyId) {
        return API_KEY_USAGE_PREFIX + apiKeyId;
    }
}
