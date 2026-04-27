package com.leo.erp.common.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

@Component
public class RedisJsonCacheSupport {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisJsonCacheSupport(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> typeReference, Supplier<T> loader) {
        if (redisTemplate != null && objectMapper != null) {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                try {
                    return objectMapper.readValue(cached, typeReference);
                } catch (JsonProcessingException ex) {
                    redisTemplate.delete(key);
                }
            }
        }

        T loaded = loader.get();
        write(key, loaded, ttl);
        return loaded;
    }

    public <T> T getOrLoad(String key, Duration ttl, Class<T> type, Supplier<T> loader) {
        if (redisTemplate != null && objectMapper != null) {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                try {
                    return objectMapper.readValue(cached, type);
                } catch (JsonProcessingException ex) {
                    redisTemplate.delete(key);
                }
            }
        }

        T loaded = loader.get();
        write(key, loaded, ttl);
        return loaded;
    }

    public void write(String key, Object value, Duration ttl) {
        if (redisTemplate == null || objectMapper == null || key == null || value == null || ttl == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("缓存序列化失败", ex);
        }
    }

    public void delete(String key) {
        if (redisTemplate == null || key == null) {
            return;
        }
        redisTemplate.delete(key);
    }

    public void delete(Collection<String> keys) {
        if (redisTemplate == null || keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
    }

    public void deleteByPattern(String pattern) {
        if (redisTemplate == null || pattern == null || pattern.isBlank()) {
            return;
        }
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return;
        }
        RedisConnection connection = connectionFactory.getConnection();
        List<String> batch = new ArrayList<>(256);
        try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(256).build())) {
            while (cursor.hasNext()) {
                batch.add(new String(cursor.next(), StandardCharsets.UTF_8));
                if (batch.size() >= 256) {
                    redisTemplate.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
            }
        } finally {
            connection.close();
        }
    }
}
