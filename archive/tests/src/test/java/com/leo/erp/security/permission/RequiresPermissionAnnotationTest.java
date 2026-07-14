package com.leo.erp.security.permission;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequiresPermissionAnnotationTest {

    @Test
    void shouldHaveDefaultValues() throws NoSuchMethodException {
        RequiresPermission annotation = TestController.class.getMethod("defaultPermission").getAnnotation(RequiresPermission.class);

        assertThat(annotation.resource()).isEmpty();
        assertThat(annotation.action()).isEmpty();
        assertThat(annotation.authenticatedOnly()).isFalse();
        assertThat(annotation.allowApiKey()).isFalse();
    }

    @Test
    void shouldSupportCustomValues() throws NoSuchMethodException {
        RequiresPermission annotation = TestController.class.getMethod("customPermission").getAnnotation(RequiresPermission.class);

        assertThat(annotation.resource()).isEqualTo("material");
        assertThat(annotation.action()).isEqualTo("read");
        assertThat(annotation.authenticatedOnly()).isTrue();
        assertThat(annotation.allowApiKey()).isTrue();
    }

    @Test
    void shouldBeRuntimeRetention() {
        assertThat(RequiresPermission.class.getAnnotation(java.lang.annotation.Retention.class)).isNotNull();
    }

    @Test
    void shouldBeMethodTarget() {
        assertThat(RequiresPermission.class.getAnnotation(java.lang.annotation.Target.class)).isNotNull();
    }

    static class TestController {
        @RequiresPermission
        public void defaultPermission() {}

        @RequiresPermission(resource = "material", action = "read", authenticatedOnly = true, allowApiKey = true)
        public void customPermission() {}
    }
}
