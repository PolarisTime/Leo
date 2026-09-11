package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareRequest;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadCompletionRequest;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2AttachmentUploadSessionController 资源型直传会话端点测试：
 * 覆盖会话创建（201 + Location 指向会话资源）与会话完成（201 + Location 指向附件资源）。
 */
@ExtendWith(MockitoExtension.class)
class V2AttachmentUploadSessionControllerTest {

    private static final String MODULE_KEY = "sales-order";

    @Mock
    private AttachmentWebService attachmentWebService;

    @Mock
    private AttachmentRecordAccessService attachmentRecordAccessService;

    @InjectMocks
    private V2AttachmentUploadSessionController controller;

    private final SecurityPrincipal principal = SecurityPrincipal.authenticated(9L, "tester", 1L);

    @BeforeEach
    void setUp() {
        lenient().when(attachmentRecordAccessService.normalizeModuleKey(MODULE_KEY)).thenReturn(MODULE_KEY);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void create_shouldReturn201WithLocationPointingToSession() {
        AttachmentDirectUploadPrepareRequest request = new AttachmentDirectUploadPrepareRequest(
                "报告.pdf", "application/pdf", 2048L, "PAGE_UPLOAD", "a".repeat(64));
        AttachmentDirectUploadPrepareResponse prepared = new AttachmentDirectUploadPrepareResponse(
                123L,
                "token-1",
                "object-key",
                "/local/object-key",
                URI.create("https://s3.example.com/presigned"),
                "PUT",
                Map.of("Content-Type", "application/pdf"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
        when(attachmentWebService.prepareDirectUpload(request, MODULE_KEY, 9L)).thenReturn(prepared);

        ResponseEntity<AttachmentDirectUploadPrepareResponse> result =
                controller.create(principal, MODULE_KEY, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(prepared);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getHeaders().getLocation().getPath())
                .endsWith("/v2.0/attachment-upload-sessions/123");
        verify(attachmentWebService).prepareDirectUpload(request, MODULE_KEY, 9L);
    }

    @Test
    void complete_shouldReturn201WithLocationPointingToAttachment() {
        AttachmentUploadResponse uploaded = mock(AttachmentUploadResponse.class);
        when(uploaded.id()).thenReturn(456L);
        when(attachmentWebService.completeDirectUpload(123L, "token-1", MODULE_KEY, 9L)).thenReturn(uploaded);

        ResponseEntity<AttachmentUploadResponse> result = controller.complete(
                principal, 123L, MODULE_KEY, new AttachmentUploadCompletionRequest("token-1"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(uploaded);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getHeaders().getLocation().getPath()).endsWith("/v2.0/attachments/456");
        verify(attachmentWebService).completeDirectUpload(123L, "token-1", MODULE_KEY, 9L);
    }
}
