package com.leo.erp.security.totp;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;

import static org.assertj.core.api.Assertions.assertThat;

class RequiresTotpVerificationAnnotationTest {

    @Test
    void shouldBeRuntimeRetention() {
        assertThat(RequiresTotpVerification.class.getAnnotation(java.lang.annotation.Retention.class)).isNotNull();
    }

    @Test
    void shouldBeMethodTarget() {
        assertThat(RequiresTotpVerification.class.getAnnotation(java.lang.annotation.Target.class)).isNotNull();
    }

    @Test
    void shouldHaveTargetElementTypeMethod() {
        java.lang.annotation.Target target = RequiresTotpVerification.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    private static class TestClass {
        @RequiresTotpVerification
        public void testMethod() {}
    }
}