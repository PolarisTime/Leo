package com.leo.erp.attachment.web;

import com.leo.erp.attachment.api.AttachmentManifestArchive;
import com.leo.erp.attachment.api.AttachmentManifestExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * V2AttachmentManifestExportController 资源型清单导出端点测试：
 * 校验 201 Created、gzip Content-Type、Content-Disposition 文件名与响应字节。
 */
@ExtendWith(MockitoExtension.class)
class V2AttachmentManifestExportControllerTest {

    @Mock
    private AttachmentManifestExporter exportService;

    @InjectMocks
    private V2AttachmentManifestExportController controller;

    @Test
    void create_shouldReturnCreatedGzipFileWithDisposition() {
        byte[] content = "manifest-content".getBytes(StandardCharsets.UTF_8);
        AttachmentManifestArchive archive = new AttachmentManifestArchive(
                "attachment-manifests/daily/2026/01/02/manifest-20260102T000000Z.jsonl.gz",
                "/local/2026/01/02/manifest-20260102T000000Z.jsonl.gz",
                "manifest-20260102T000000Z.jsonl.gz",
                "application/gzip",
                content,
                2,
                3
        );
        when(exportService.exportDailyArchive()).thenReturn(archive);

        ResponseEntity<byte[]> result = controller.create();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/gzip"));
        assertThat(result.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("manifest-20260102T000000Z.jsonl.gz");
        assertThat(result.getBody()).containsExactly(content);
    }
}
