package com.leo.erp.security.jwt;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class SessionActivityService {

    private static final String SESSION_ACTIVITY_PREFIX = "session:activity:";
    private static final Duration ONLINE_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;

    public SessionActivityService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void touchSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(sessionKey(sessionId), String.valueOf(System.currentTimeMillis()), ONLINE_TTL);
    }

    public void clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        redisTemplate.delete(sessionKey(sessionId));
    }

    public Map<String, LocalDateTime> resolveLastActiveAt(Collection<String> sessionIds) {
        List<String> normalizedIds = sessionIds == null
                ? List.of()
                : sessionIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        List<String> keys = normalizedIds.stream().map(this::sessionKey).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<String, LocalDateTime> result = new HashMap<>();
        IntStream.range(0, Math.min(normalizedIds.size(), values.size()))
                .forEach(index -> {
                    String value = values.get(index);
                    if (value != null && !value.isBlank()) {
                        result.put(normalizedIds.get(index), toDateTime(value));
                    }
                });
        return result;
    }

    private String sessionKey(String sessionId) {
        return SESSION_ACTIVITY_PREFIX + sessionId;
    }

    private LocalDateTime toDateTime(String rawValue) {
        long epochMillis = Long.parseLong(rawValue);
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
