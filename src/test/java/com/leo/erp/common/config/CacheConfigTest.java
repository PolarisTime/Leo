package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.master.supplier.web.dto.SupplierOptionResponse;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

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
    void cacheKeyPrefix_constantValue() {
        assertThat(CacheConfig.CACHE_KEY_PREFIX).isEqualTo("leo:cache:v3:");
    }

    @Test
    void cacheConfiguration_usesVersionedPrefix() {
        GenericJackson2JsonRedisSerializer serializer = CacheConfig.redisValueSerializer(new ObjectMapper());

        RedisCacheConfiguration configuration = CacheConfig.cacheConfiguration(Duration.ofMinutes(1), serializer);

        assertThat(configuration.getKeyPrefixFor(CacheConfig.CACHE_STATIC))
                .isEqualTo("leo:cache:v3:static::");
    }

    @Test
    void redisValueSerializer_restoresConcreteRecordType() {
        GenericJackson2JsonRedisSerializer serializer = CacheConfig.redisValueSerializer(new ObjectMapper().findAndRegisterModules());
        CompanySettingResponse expected = new CompanySettingResponse(
                1L,
                "公司A",
                "税号",
                "银行",
                "账号",
                new BigDecimal("0.1300"),
                List.of(),
                "正常",
                null
        );

        Object decoded = serializer.deserialize(serializer.serialize(expected));

        assertThat(decoded).isInstanceOf(CompanySettingResponse.class);
        assertThat(((CompanySettingResponse) decoded).companyName()).isEqualTo("公司A");
    }

    @Test
    void redisValueSerializer_restoresStreamToListCacheValue() {
        GenericJackson2JsonRedisSerializer serializer = CacheConfig.redisValueSerializer(new ObjectMapper().findAndRegisterModules());
        List<SupplierOptionResponse> expected = Stream.of(new SupplierOptionResponse(1L, "供应商A", "供应商A"))
                .toList();

        Object decoded = serializer.deserialize(serializer.serialize(expected));

        assertThat(decoded).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<SupplierOptionResponse> actual = (List<SupplierOptionResponse>) decoded;
        assertThat(actual).containsExactlyElementsOf(expected);
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
