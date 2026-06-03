package com.leo.erp.security.permission;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitAnnotationTest {

    @Test
    void shouldHaveDefaultValues() throws NoSuchMethodException {
        RateLimit annotation = TestController.class.getMethod("defaultRateLimit").getAnnotation(RateLimit.class);

        assertThat(annotation.rate()).isEqualTo(-1);
        assertThat(annotation.capacity()).isEqualTo(-1);
        assertThat(annotation.tokens()).isEqualTo(1);
        assertThat(annotation.maxRequests()).isEqualTo(10);
        assertThat(annotation.duration()).isEqualTo(1);
        assertThat(annotation.timeUnit()).isEqualTo(TimeUnit.MINUTES);
    }

    @Test
    void shouldSupportCustomValues() throws NoSuchMethodException {
        RateLimit annotation = TestController.class.getMethod("customRateLimit").getAnnotation(RateLimit.class);

        assertThat(annotation.rate()).isEqualTo(10.0);
        assertThat(annotation.capacity()).isEqualTo(100);
        assertThat(annotation.tokens()).isEqualTo(5);
        assertThat(annotation.maxRequests()).isEqualTo(50);
        assertThat(annotation.duration()).isEqualTo(2);
        assertThat(annotation.timeUnit()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void shouldBeRuntimeRetention() {
        assertThat(RateLimit.class.getAnnotation(java.lang.annotation.Retention.class)).isNotNull();
    }

    @Test
    void shouldBeMethodTarget() {
        assertThat(RateLimit.class.getAnnotation(java.lang.annotation.Target.class)).isNotNull();
    }

    static class TestController {
        @RateLimit
        public void defaultRateLimit() {}

        @RateLimit(rate = 10.0, capacity = 100, tokens = 5, maxRequests = 50, duration = 2, timeUnit = TimeUnit.SECONDS)
        public void customRateLimit() {}
    }
}
