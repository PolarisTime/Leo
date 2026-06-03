package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CacheConfigTest {

    @Test
    void cacheStatic_constantValue() {
        assertThat(CacheConfig.CACHE_STATIC).isEqualTo("static");
    }

    @Test
    void cacheHot_constantValue() {
        assertThat(CacheConfig.CACHE_HOT).isEqualTo("hot");
    }

    @Test
    void cacheManager_createsManagerWithConfigs() {
        CacheConfig cacheConfig = new CacheConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RedisTuningProperties props = new RedisTuningProperties();
        props.getCache().setStaticTtl(Duration.ofHours(1));
        props.getCache().setHotTtl(Duration.ofMinutes(5));

        RedisCacheManager manager = cacheConfig.cacheManager(connectionFactory, objectMapper, props);

        assertThat(manager).isNotNull();
    }

    @Test
    void cacheManager_handlesDefaultTtl() {
        CacheConfig cacheConfig = new CacheConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RedisTuningProperties props = new RedisTuningProperties();

        RedisCacheManager manager = cacheConfig.cacheManager(connectionFactory, objectMapper, props);

        assertThat(manager).isNotNull();
    }
}
