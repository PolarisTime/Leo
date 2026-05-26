package com.leo.erp.security.jwt;

import com.leo.erp.common.config.RedisTuningProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class SessionActivityService {

    private static final String SESSION_ACTIVITY_PREFIX = "session:activity:";

    private final StringRedisTemplate redisTemplate;
    private final RedisTuningProperties redisTuningProperties;
    private final DefaultRedisScript<Long> touchScript;

    @Autowired
    public SessionActivityService(StringRedisTemplate redisTemplate, RedisTuningProperties redisTuningProperties) {
        this.redisTemplate = redisTemplate;
        this.redisTuningProperties = redisTuningProperties;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("db/session_activity_touch.lua"));
        script.setResultType(Long.class);
        this.touchScript = script;
    }

    public void touchSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        Duration onlineTtl = redisTuningProperties.sessionOnlineTtl();
        Duration writeInterval = redisTuningProperties.sessionActivityWriteInterval();
        try {
            redisTemplate.execute(
                    touchScript,
                    List.of(sessionKey(sessionId)),
                    String.valueOf(now),
                    String.valueOf(onlineTtl.toMillis()),
                    String.valueOf(writeInterval.toMillis())
            );
        } catch (RuntimeException ex) {
            log.warn("Redis session activity throttled touch failed, falling back to direct SET", ex);
            redisTemplate.opsForValue().set(sessionKey(sessionId), String.valueOf(now), onlineTtl);
        }
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

        Map<String, LocalDateTime> result = new HashMap<>();
        int batchSize = redisTuningProperties.sessionMgetBatchSize();
        for (int start = 0; start < normalizedIds.size(); start += batchSize) {
            List<String> batchIds = normalizedIds.subList(start, Math.min(start + batchSize, normalizedIds.size()));
            List<String> keys = batchIds.stream().map(this::sessionKey).toList();
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null || values.isEmpty()) {
                continue;
            }
            for (int index = 0; index < Math.min(batchIds.size(), values.size()); index++) {
                String value = values.get(index);
                if (value != null && !value.isBlank()) {
                    Optional<LocalDateTime> dateTime = toDateTime(value);
                    if (dateTime.isPresent()) {
                        result.put(batchIds.get(index), dateTime.get());
                    }
                }
            }
        }
        return result;
    }

    private String sessionKey(String sessionId) {
        return SESSION_ACTIVITY_PREFIX + sessionId;
    }

    private Optional<LocalDateTime> toDateTime(String rawValue) {
        try {
            long epochMillis = Long.parseLong(rawValue);
            return Optional.of(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
        } catch (NumberFormatException ex) {
            log.debug("Invalid Redis session activity timestamp: {}", rawValue);
            return Optional.empty();
        }
    }
}
