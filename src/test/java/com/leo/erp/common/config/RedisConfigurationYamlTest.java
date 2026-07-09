package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigurationYamlTest {

    @Test
    void applicationYamlUsesRedisDatabaseFourByDefault() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var propertySource = loader.load("application", new ClassPathResource("application.yml")).getFirst();

        assertThat(propertySource.getProperty("spring.data.redis.database"))
                .isEqualTo("${SPRING_DATA_REDIS_DATABASE:4}");
    }

    @Test
    void devEnvScriptUsesRedisDatabaseFourByDefault() throws Exception {
        String script = Files.readString(Path.of("scripts/env/common.sh"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("SPRING_DATA_REDIS_DATABASE=\"${SPRING_DATA_REDIS_DATABASE:-4}\"");
    }
}
