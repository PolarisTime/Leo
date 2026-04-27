package com.leo.erp.attachment.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentFilenameResolverTest {

    private final AttachmentFilenameResolver resolver = new AttachmentFilenameResolver();

    @Test
    void shouldRenderFileNameWithRulePlaceholders() {
        String fileName = resolver.buildFileName(
                "{yyyyMMdd}_{originName}_{ext}",
                "合同 扫描件.pdf",
                "application/pdf",
                LocalDateTime.of(2026, 4, 24, 12, 30, 45)
        );

        assertThat(fileName).startsWith("20260424_合同_扫描件_pdf");
        assertThat(fileName).endsWith(".pdf");
    }

    @Test
    void shouldUseContentTypeExtensionForClipboardUpload() {
        String fileName = resolver.preview("{yyyyMMddHHmmss}_{originName}", "");

        assertThat(fileName).isEqualTo("20260424123045_clipboard.pdf");
    }

    @Test
    void shouldSanitizeUnsafeCharacters() {
        String fileName = resolver.preview("{originName}", "../报价单?.xlsx");

        assertThat(fileName).isEqualTo("报价单.xlsx");
    }
}
