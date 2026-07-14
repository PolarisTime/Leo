package com.leo.erp.attachment.service;

import com.leo.erp.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentDirectUploadTokenServiceSpringTest {

    @Test
    void shouldCreateBeanWithJwtPropertiesConstructor() {
        new ApplicationContextRunner()
                .withBean(JwtProperties.class, () -> new JwtProperties(
                        "leo-erp",
                        "leo-direct-upload-test-secret-must-be-long-enough",
                        1_800_000L,
                        604_800_000L
                ))
                .withBean(AttachmentDirectUploadTokenService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttachmentDirectUploadTokenService.class);
                });
    }
}
