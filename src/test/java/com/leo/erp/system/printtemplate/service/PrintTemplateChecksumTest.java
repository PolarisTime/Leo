package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintTemplateChecksumTest {

    @Test
    void shouldCalculateSha256Checksum() {
        assertThat(PrintTemplateChecksum.sha256("leo"))
                .isEqualTo("8535e86c8118bbbb0a18ac72d15d3a2b37b18d1bce1611fc60165f322cf57386");
    }

    @Test
    void shouldWrapUnavailableSha256Algorithm() {
        try (var digest = Mockito.mockStatic(MessageDigest.class)) {
            digest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("missing"));

            assertThatThrownBy(() -> PrintTemplateChecksum.sha256("leo"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("当前运行环境不支持 SHA-256")
                    .hasCauseInstanceOf(NoSuchAlgorithmException.class);
        }
    }
}
