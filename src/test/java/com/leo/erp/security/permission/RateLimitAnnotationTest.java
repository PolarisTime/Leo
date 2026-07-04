package com.leo.erp.security.permission;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitAnnotationTest {

    @Test
    void shouldHaveDefaultValues() throws NoSuchMethodException {
        RateLimit annotation = TestController.class.getMethod("defaultRateLimit").getAnnotation(RateLimit.class);

        assertThat(annotation.rate()).isEqualTo(-1);
        assertThat(annotation.capacity()).isEqualTo(-1);
        assertThat(annotation.tokens()).isEqualTo(1);
    }

    @Test
    void shouldSupportCustomValues() throws NoSuchMethodException {
        RateLimit annotation = TestController.class.getMethod("customRateLimit").getAnnotation(RateLimit.class);

        assertThat(annotation.rate()).isEqualTo(10.0);
        assertThat(annotation.capacity()).isEqualTo(100);
        assertThat(annotation.tokens()).isEqualTo(5);
    }

    @Test
    void shouldExposeOnlyTokenBucketAttributes() {
        assertThat(Arrays.stream(RateLimit.class.getDeclaredMethods()).map(Method::getName))
                .containsExactlyInAnyOrder("rate", "capacity", "tokens");
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

        @RateLimit(rate = 10.0, capacity = 100, tokens = 5)
        public void customRateLimit() {}
    }
}
