package com.leo.erp.attachment.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void shouldRenderChineseDatePlaceholders() {
        String fileName = resolver.preview("{年月日}_{年月日时分秒}_{originName}", "合同.pdf");

        assertThat(fileName).isEqualTo("20260424_20260424123045_合同.pdf");
    }

    @Test
    void shouldSanitizeUnsafeCharacters() {
        String fileName = resolver.preview("{originName}", "../报价单?.xlsx");

        assertThat(fileName).isEqualTo("报价单.xlsx");
    }

    @Test
    void shouldRejectBlankRenderedName() {
        assertThatThrownBy(() -> resolver.preview("...", "合同.pdf"))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("上传命名规则生成的文件名为空");
    }

    @Test
    void shouldAppendFallbackExtensionWhenOriginalHasNoExtension() {
        String fileName = resolver.buildFileName(
                "{originName}",
                "合同",
                null,
                LocalDateTime.of(2026, 4, 24, 12, 30, 45)
        );

        assertThat(fileName).isEqualTo("合同.bin");
    }

    @Test
    void shouldNotDuplicateExtensionWhenPatternAlreadyEndsWithExtension() {
        String fileName = resolver.preview("{originName}.{ext}", "合同.PDF");

        assertThat(fileName).isEqualTo("合同.pdf");
    }

    @Test
    void shouldUsePreviewContentTypeWhenOriginalExtensionIsMissing() {
        String fileName = resolver.preview("{originName}", "合同.");

        assertThat(fileName).isEqualTo("合同.pdf");
    }

    @Test
    void shouldUseClipboardAndBinForNullFilenameAndContentType() {
        AttachmentFilenameResolver.FilenameParts parts = resolver.parseFilenameParts(null, null);

        assertThat(parts.baseName()).isEqualTo("clipboard");
        assertThat(parts.extension()).isEqualTo("bin");
    }

    @Test
    void shouldParseHiddenFileNameAsClipboardWithFallbackExtension() {
        AttachmentFilenameResolver.FilenameParts parts = resolver.parseFilenameParts(".env", "text/plain");

        assertThat(parts.baseName()).isEqualTo("env");
        assertThat(parts.extension()).isEqualTo("txt");
    }

    @Test
    void shouldSanitizeAndLimitExtensionLength() {
        AttachmentFilenameResolver.FilenameParts parts = resolver.parseFilenameParts(
                "合同.very-long_extension-name",
                null
        );

        assertThat(parts.baseName()).isEqualTo("合同");
        assertThat(parts.extension()).isEqualTo("verylongextensio");
    }

    @Test
    void shouldResolveKnownContentTypes() {
        assertThat(resolver.parseFilenameParts(null, "image/png").extension()).isEqualTo("png");
        assertThat(resolver.parseFilenameParts(null, "image/jpeg").extension()).isEqualTo("jpg");
        assertThat(resolver.parseFilenameParts(null, "image/gif").extension()).isEqualTo("gif");
        assertThat(resolver.parseFilenameParts(null, "image/webp").extension()).isEqualTo("webp");
        assertThat(resolver.parseFilenameParts(null, "text/plain").extension()).isEqualTo("txt");
        assertThat(resolver.parseFilenameParts(null, "application/vnd.openxmlformats-officedocument.wordprocessingml.document").extension()).isEqualTo("docx");
        assertThat(resolver.parseFilenameParts(null, "application/msword").extension()).isEqualTo("doc");
        assertThat(resolver.parseFilenameParts(null, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").extension()).isEqualTo("xlsx");
        assertThat(resolver.parseFilenameParts(null, "application/vnd.ms-excel").extension()).isEqualTo("xls");
        assertThat(resolver.parseFilenameParts(null, "application/octet-stream").extension()).isEqualTo("bin");
        assertThat(resolver.parseFilenameParts(null, " ").extension()).isEqualTo("bin");
    }

    @Test
    void shouldReturnRenderedNameWhenExtensionIsBlank() {
        AttachmentFilenameResolver blankExtensionResolver = new AttachmentFilenameResolver() {
            @Override
            public AttachmentFilenameResolver.FilenameParts parseFilenameParts(String originalFilename, String contentType) {
                return new AttachmentFilenameResolver.FilenameParts("合同", "");
            }
        };

        String fileName = ReflectionTestUtils.invokeMethod(
                blankExtensionResolver,
                "renderFileName",
                "{originName}",
                "合同",
                "application/pdf",
                LocalDateTime.of(2026, 4, 24, 12, 30, 45),
                "1777005045000",
                "preview1"
        );

        assertThat(fileName).isEqualTo("合同");
    }

    @Test
    void shouldSanitizeNullNameAndExtension() {
        assertThat((String) ReflectionTestUtils.invokeMethod(resolver, "sanitizeBaseName", (Object) null))
                .isEmpty();
        assertThat((String) ReflectionTestUtils.invokeMethod(resolver, "sanitizeExtension", (Object) null))
                .isEmpty();
    }
}
