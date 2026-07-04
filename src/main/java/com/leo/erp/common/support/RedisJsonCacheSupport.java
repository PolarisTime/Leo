package com.leo.erp.common.support;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.RedisTuningProperties;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Optional;

@Slf4j
@Component
public class RedisJsonCacheSupport {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTuningProperties redisTuningProperties;
    private AfterCommitExecutor afterCommitExecutor;

    public RedisJsonCacheSupport(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 RedisTuningProperties redisTuningProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisTuningProperties = redisTuningProperties;
    }

    @Autowired(required = false)
    public void setAfterCommitExecutor(AfterCommitExecutor afterCommitExecutor) {
        this.afterCommitExecutor = afterCommitExecutor;
    }

    public <T> Optional<T> read(String key, TypeReference<T> typeReference) {
        if (redisTemplate == null || objectMapper == null || key == null || typeReference == null) {
            return Optional.empty();
        }
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                return Optional.ofNullable(objectMapper.readValue(cached, typeReference));
            }
        } catch (JsonProcessingException ex) {
            delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis cache read failed, key={}", key, ex);
        }
        return Optional.empty();
    }

    public <T> Optional<T> read(String key, Class<T> type) {
        if (redisTemplate == null || objectMapper == null || key == null || type == null) {
            return Optional.empty();
        }
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                return Optional.ofNullable(objectMapper.readValue(cached, type));
            }
        } catch (JsonProcessingException ex) {
            delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis cache read failed, key={}", key, ex);
        }
        return Optional.empty();
    }

    public void write(String key, Object value, Duration ttl) {
        if (redisTemplate == null || objectMapper == null || key == null || value == null || ttl == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), redisTuningProperties.withTtlJitter(ttl));
        } catch (JsonProcessingException ex) {
            log.warn("Redis cache serialization failed, key={}", key, ex);
        } catch (RuntimeException ex) {
            log.warn("Redis cache write failed, key={}", key, ex);
        }
    }

    public void delete(String key) {
        if (redisTemplate == null || key == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis cache delete failed, key={}", key, ex);
        }
    }

    public void deleteAfterCommit(String key) {
        if (key == null) {
            return;
        }
        if (afterCommitExecutor == null) {
            delete(key);
            return;
        }
        afterCommitExecutor.run(() -> delete(key));
    }

    public void delete(Collection<String> keys) {
        if (redisTemplate == null || keys == null || keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (RuntimeException ex) {
            log.warn("Redis cache delete failed, keys={}", keys, ex);
        }
    }

    public void deleteAfterCommit(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        if (afterCommitExecutor == null) {
            delete(keys);
            return;
        }
        afterCommitExecutor.run(() -> delete(keys));
    }

    public void deleteByPattern(String pattern) {
        if (redisTemplate == null || pattern == null || pattern.isBlank()) {
            return;
        }
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return;
        }
        RedisConnection connection = null;
        int scanBatchSize = redisTuningProperties.scanBatchSize();
        int deleteBatchSize = redisTuningProperties.deleteBatchSize();
        int maxScanKeys = redisTuningProperties.maxScanKeys();
        List<String> batch = new ArrayList<>(deleteBatchSize);
        int totalDeleted = 0;
        try {
            connection = connectionFactory.getConnection();
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(scanBatchSize).build())) {
                while (cursor.hasNext()) {
                    batch.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    if (batch.size() >= deleteBatchSize) {
                        redisTemplate.delete(batch);
                        totalDeleted += batch.size();
                        batch.clear();
                        if (totalDeleted >= maxScanKeys) {
                            log.warn("Redis pattern delete reached max scan limit, pattern={}, deleted={}", pattern, totalDeleted);
                            break;
                        }
                    }
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
                totalDeleted += batch.size();
            }
            if (totalDeleted > 0) {
                log.info("Redis pattern delete completed, pattern={}, deleted={}", pattern, totalDeleted);
            }
        } catch (RuntimeException ex) {
            log.warn("Redis cache pattern delete failed, pattern={}", pattern, ex);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (RuntimeException ex) {
                    log.warn("Redis connection close failed after pattern delete, pattern={}", pattern, ex);
                }
            }
        }
    }
}
