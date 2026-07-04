package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigurationTest {

    @Test
    void applicationYamlDefinesTracingAndOtlpDefaults() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var propertySource = loader.load("application", new ClassPathResource("application.yml")).getFirst();

        assertThat(propertySource.getProperty("management.tracing.sampling.probability"))
                .isEqualTo("${LEO_TRACING_SAMPLING_PROBABILITY:1.0}");
        assertThat(propertySource.getProperty("management.otlp.tracing.export.enabled"))
                .isEqualTo("${LEO_OTLP_TRACING_EXPORT_ENABLED:false}");
        assertThat(propertySource.getProperty("management.otlp.tracing.endpoint"))
                .isEqualTo("${LEO_OTLP_TRACING_ENDPOINT:http://localhost:4318/v1/traces}");
        assertThat(propertySource.getProperty("management.otlp.tracing.connect-timeout"))
                .isEqualTo("${LEO_OTLP_TRACING_CONNECT_TIMEOUT:1s}");
        assertThat(propertySource.getProperty("management.otlp.tracing.timeout"))
                .isEqualTo("${LEO_OTLP_TRACING_TIMEOUT:5s}");
        assertThat(propertySource.getProperty("logging.pattern.correlation"))
                .isEqualTo("[%X{traceId:-},%X{spanId:-}] ");
        assertThat(propertySource.getProperty("logging.include-application-name"))
                .isEqualTo(false);
    }

    @Test
    void productionProfileLowersDefaultTracingSamplingRate() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var propertySource = loader.load("application-prod", new ClassPathResource("application-prod.yml")).getFirst();

        assertThat(propertySource.getProperty("management.tracing.sampling.probability"))
                .isEqualTo("${LEO_TRACING_SAMPLING_PROBABILITY:0.1}");
    }

    @Test
    void logbackPatternIncludesTraceAndSpanIds() throws Exception {
        String logback = new ClassPathResource("logback-spring.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(logback).contains("[%X{traceId:-},%X{spanId:-}]");
    }
}
