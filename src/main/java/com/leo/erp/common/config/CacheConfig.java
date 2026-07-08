package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_STATIC = "static";
    public static final String CACHE_HOT = "hot";
    public static final String CACHE_OPTIONS = "options";
    public static final String CACHE_KEY_PREFIX = "leo:cache:v4:";
    private static final String TYPE_HINT_PROPERTY = "@class";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          ObjectMapper objectMapper,
                                          RedisTuningProperties redisTuningProperties) {
        GenericJackson2JsonRedisSerializer serializer = redisValueSerializer(objectMapper);

        RedisCacheConfiguration staticConfig = cacheConfiguration(
                redisTuningProperties.withTtlJitter(redisTuningProperties.getCache().getStaticTtl()),
                serializer
        );
        RedisCacheConfiguration hotConfig = cacheConfiguration(
                redisTuningProperties.withTtlJitter(redisTuningProperties.getCache().getHotTtl()),
                serializer
        );
        RedisCacheConfiguration optionsConfig = cacheConfiguration(
                redisTuningProperties.withTtlJitter(redisTuningProperties.getCache().getOptionsTtl()),
                serializer
        );

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(CACHE_STATIC, staticConfig)
                .withCacheConfiguration(CACHE_HOT, hotConfig)
                .withCacheConfiguration(CACHE_OPTIONS, optionsConfig)
                .transactionAware()
                .build();
    }

    static GenericJackson2JsonRedisSerializer redisValueSerializer(ObjectMapper objectMapper) {
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.activateDefaultTypingAsProperty(
                redisCacheTypeValidator(),
                DefaultTyping.EVERYTHING,
                TYPE_HINT_PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(redisObjectMapper);
    }

    private static PolymorphicTypeValidator redisCacheTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.leo.erp.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .build();
    }

    static RedisCacheConfiguration cacheConfiguration(Duration ttl,
                                                      GenericJackson2JsonRedisSerializer serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .prefixCacheNameWith(CACHE_KEY_PREFIX)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}
