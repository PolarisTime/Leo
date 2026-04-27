package com.leo.erp.security.jwt;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AccessTokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "access_token:blacklist:user:";
    private static final String SESSION_BLACKLIST_PREFIX = "access_token:blacklist:session:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public AccessTokenBlacklistService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 将用户加入 access token 黑名单，TTL 为 access token 的过期时间
     */
    public void blacklistUser(Long userId) {
        String key = BLACKLIST_PREFIX + userId;
        long ttlMs = jwtProperties.accessExpirationMs();
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), ttlMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 检查用户是否在黑名单中
     */
    public boolean isBlacklisted(Long userId) {
        String key = BLACKLIST_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取用户被拉黑的时间戳（毫秒）
     */
    public long getBlacklistTime(Long userId) {
        String key = BLACKLIST_PREFIX + userId;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0;
    }

    public void blacklistSession(String sessionId) {
        String key = SESSION_BLACKLIST_PREFIX + sessionId;
        long ttlMs = jwtProperties.accessExpirationMs();
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), ttlMs, TimeUnit.MILLISECONDS);
    }

    public boolean isSessionBlacklisted(String sessionId) {
        String key = SESSION_BLACKLIST_PREFIX + sessionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
