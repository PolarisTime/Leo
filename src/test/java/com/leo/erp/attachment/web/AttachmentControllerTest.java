package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentDownloadResource;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttachmentController 资源型内容端点的极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

    private static final String MODULE_KEY = "sales-order";
    private static final String ACCESS_KEY = "stored-key";

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private AttachmentWebService attachmentWebService;

    @Mock
    private AttachmentRecordAccessService attachmentRecordAccessService;

    @InjectMocks
    private AttachmentController controller;

    private final SecurityPrincipal principal = SecurityPrincipal.authenticated(9L, "tester", 1L);

    @BeforeEach
    void setUp() {
        lenient().when(attachmentRecordAccessService.normalizeModuleKey(MODULE_KEY)).thenReturn(MODULE_KEY);
    }

    private AttachmentDownloadResource downloadResource(String fileName, MediaType contentType, boolean inline) {
        String disposition = (inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
        Resource resource = new ByteArrayResource("payload".getBytes(StandardCharsets.UTF_8));
        return new AttachmentDownloadResource(resource, contentType, disposition);
    }

    @Test
    void content_shouldReturn200WithContentTypeAndAttachmentDisposition() {
        when(attachmentService.createPresignedAccessUrl(1L, ACCESS_KEY, false)).thenReturn(null);
        when(attachmentService.loadDownloadResource(1L, ACCESS_KEY, false))
                .thenReturn(downloadResource("报告 2026.pdf", MediaType.APPLICATION_PDF, false));

        ResponseEntity<Resource> response = controller.content(principal, 1L, MODULE_KEY, ACCESS_KEY, "attachment");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(contentDisposition).startsWith("attachment;");
        assertThat(contentDisposition).contains("filename*=UTF-8''");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void content_shouldUseInlineDispositionWhenRequested() {
        when(attachmentService.createPresignedAccessUrl(2L, ACCESS_KEY, true)).thenReturn(null);
        when(attachmentService.loadDownloadResource(2L, ACCESS_KEY, true))
                .thenReturn(downloadResource("preview.png", MediaType.IMAGE_PNG, true));

        ResponseEntity<Resource> response = controller.content(principal, 2L, MODULE_KEY, ACCESS_KEY, "inline");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
        verify(attachmentService).loadDownloadResource(2L, ACCESS_KEY, true);
    }

    @Test
    void content_shouldTreatCaseInsensitiveAttachmentAsDownload() {
        when(attachmentService.createPresignedAccessUrl(3L, ACCESS_KEY, false)).thenReturn(null);
        when(attachmentService.loadDownloadResource(3L, ACCESS_KEY, false))
                .thenReturn(downloadResource("report.xlsx", MediaType.APPLICATION_OCTET_STREAM, false));

        ResponseEntity<Resource> response = controller.content(principal, 3L, MODULE_KEY, ACCESS_KEY, "ATTACHMENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(attachmentService).loadDownloadResource(3L, ACCESS_KEY, false);
    }

    @Test
    void content_shouldRedirectWhenPresignedUrlAvailable() {
        when(attachmentService.createPresignedAccessUrl(eq(4L), eq(ACCESS_KEY), eq(true)))
                .thenReturn(new AttachmentService.PresignedAttachmentUrl(
                        java.net.URI.create("https://s3.example.com/presigned"), true));

        ResponseEntity<Resource> response = controller.content(principal, 4L, MODULE_KEY, ACCESS_KEY, "inline");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
                .hasToString("https://s3.example.com/presigned");
    }

    @Test
    void content_shouldRejectInvalidDisposition() {
        assertThatThrownBy(() -> controller.content(principal, 5L, MODULE_KEY, ACCESS_KEY, "stream"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("disposition 仅支持 inline 或 attachment");
    }

    @Test
    void content_shouldPropagateNotFoundAsBusinessError() {
        doThrow(new BusinessException(ErrorCode.NOT_FOUND, "附件不存在"))
                .when(attachmentRecordAccessService)
                .assertAttachmentAccessible(principal, MODULE_KEY, 404L);

        assertThatThrownBy(() -> controller.content(principal, 404L, MODULE_KEY, ACCESS_KEY, "attachment"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void create_shouldReturn201WithLocationPointingToAttachment() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AttachmentUploadResponse uploaded = mock(AttachmentUploadResponse.class);
        when(uploaded.id()).thenReturn(456L);
        when(attachmentWebService.upload(any(), eq("PAGE_UPLOAD"), eq(MODULE_KEY), eq(9L)))
                .thenReturn(uploaded);
        MultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<AttachmentUploadResponse> response =
                controller.create(principal, MODULE_KEY, file, "PAGE_UPLOAD");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).endsWith("/v2.0/attachments/456");
        assertThat(response.getBody()).isSameAs(uploaded);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void create_shouldRequireAttachmentsCreatePermissionAndIdempotency() throws Exception {
        Method create = AttachmentController.class.getMethod(
                "create", SecurityPrincipal.class, String.class, MultipartFile.class, String.class);
        RequirePermission permission = create.getAnnotation(RequirePermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(PermissionCodes.ATTACHMENTS_CREATE);
        assertThat(create.getAnnotation(IdempotencyRequired.class)).isNotNull();
    }
}
