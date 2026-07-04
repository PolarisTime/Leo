package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

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
    void cacheOptions_constantValue() {
        assertThat(CacheConfig.CACHE_OPTIONS).isEqualTo("options");
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

    @Test
    void cacheManager_isRegisteredWhenRedisConnectionFactoryExists() {
        new ApplicationContextRunner()
                .withUserConfiguration(CacheConfig.class, CacheConfigTestDependencies.class)
                .run(context -> assertThat(context).hasSingleBean(RedisCacheManager.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CacheConfigTestDependencies {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RedisTuningProperties redisTuningProperties() {
            return new RedisTuningProperties();
        }
    }
}
