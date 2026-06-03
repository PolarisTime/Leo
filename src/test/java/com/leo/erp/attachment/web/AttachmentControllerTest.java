package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentDownloadResource;
import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentControllerTest {

    private final AttachmentService attachmentService = mock(AttachmentService.class);
    private final AttachmentWebService attachmentWebService = mock(AttachmentWebService.class);
    private final ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
    private final AttachmentRecordAccessService attachmentRecordAccessService = mock(AttachmentRecordAccessService.class);
    private final SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
    private final AttachmentController controller = new AttachmentController(
            attachmentService, attachmentWebService, modulePermissionGuard, attachmentRecordAccessService, systemSwitchService);

    @Test
    void uploadReturnsUploadedAttachment() throws IOException {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "content".getBytes());
        AttachmentUploadResponse uploadResponse = mock(AttachmentUploadResponse.class);

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "update")).thenReturn("sales-order");
        when(attachmentWebService.upload(eq(file), eq(null), eq("sales-order"))).thenReturn(uploadResponse);

        ApiResponse<AttachmentUploadResponse> response = controller.upload(principal, "sales-order", file, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("上传成功");
        verify(attachmentWebService).upload(file, null, "sales-order");
    }

    @Test
    void downloadReturnsFileResponse() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Resource resource = mock(Resource.class);
        AttachmentDownloadResource downloadResource = new AttachmentDownloadResource(
                resource, MediaType.APPLICATION_OCTET_STREAM, "attachment; filename=\"test.txt\"");

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(attachmentService.loadDownloadResource(eq(1L), eq("access-key"), eq(false), eq(false), eq("user"))).thenReturn(downloadResource);
        when(principal.getUsername()).thenReturn("user");
        when(systemSwitchService.shouldWatermarkAttachments()).thenReturn(false);

        ResponseEntity<Resource> response = controller.download(principal, 1L, "sales-order", "access-key");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(resource);
    }

    @Test
    void previewReturnsFileResponse() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Resource resource = mock(Resource.class);
        AttachmentDownloadResource downloadResource = new AttachmentDownloadResource(
                resource, MediaType.APPLICATION_OCTET_STREAM, "inline; filename=\"test.txt\"");

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(attachmentService.loadDownloadResource(eq(1L), eq("access-key"), eq(true), eq(false), eq("user"))).thenReturn(downloadResource);
        when(principal.getUsername()).thenReturn("user");
        when(systemSwitchService.shouldWatermarkAttachments()).thenReturn(false);

        ResponseEntity<Resource> response = controller.preview(principal, 1L, "sales-order", "access-key");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(resource);
    }

    @Test
    void downloadAppliesWatermarkWhenEnabledAndNotAdmin() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Resource resource = mock(Resource.class);
        AttachmentDownloadResource downloadResource = new AttachmentDownloadResource(
                resource, MediaType.APPLICATION_OCTET_STREAM, "attachment; filename=\"test.txt\"");

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(principal.getUsername()).thenReturn("user");
        doReturn(Collections.emptyList()).when(principal).getAuthorities();
        when(systemSwitchService.shouldWatermarkAttachments()).thenReturn(true);
        when(attachmentService.loadDownloadResource(eq(1L), eq("access-key"), eq(false), eq(true), eq("user"))).thenReturn(downloadResource);

        ResponseEntity<Resource> response = controller.download(principal, 1L, "sales-order", "access-key");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(attachmentService).loadDownloadResource(1L, "access-key", false, true, "user");
    }

    @Test
    void downloadSkipsWatermarkWhenEnabledButAdmin() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Resource resource = mock(Resource.class);
        AttachmentDownloadResource downloadResource = new AttachmentDownloadResource(
                resource, MediaType.APPLICATION_OCTET_STREAM, "attachment; filename=\"test.txt\"");
        org.springframework.security.core.GrantedAuthority adminAuthority =
                mock(org.springframework.security.core.GrantedAuthority.class);

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(principal.getUsername()).thenReturn("admin");
        when(adminAuthority.getAuthority()).thenReturn("ROLE_ADMIN");
        Collection<? extends org.springframework.security.core.GrantedAuthority> adminAuthorities = List.of(adminAuthority);
        doReturn(adminAuthorities).when(principal).getAuthorities();
        when(systemSwitchService.shouldWatermarkAttachments()).thenReturn(true);
        when(attachmentService.loadDownloadResource(eq(1L), eq("access-key"), eq(false), eq(false), eq("admin"))).thenReturn(downloadResource);

        ResponseEntity<Resource> response = controller.download(principal, 1L, "sales-order", "access-key");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(attachmentService).loadDownloadResource(1L, "access-key", false, false, "admin");
    }

    @Test
    void previewAppliesWatermarkWhenEnabledAndNotAdmin() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Resource resource = mock(Resource.class);
        AttachmentDownloadResource downloadResource = new AttachmentDownloadResource(
                resource, MediaType.APPLICATION_OCTET_STREAM, "inline; filename=\"test.txt\"");

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(principal.getUsername()).thenReturn("user");
        doReturn(Collections.emptyList()).when(principal).getAuthorities();
        when(systemSwitchService.shouldWatermarkAttachments()).thenReturn(true);
        when(attachmentService.loadDownloadResource(eq(1L), eq("access-key"), eq(true), eq(true), eq("user"))).thenReturn(downloadResource);

        ResponseEntity<Resource> response = controller.preview(principal, 1L, "sales-order", "access-key");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(attachmentService).loadDownloadResource(1L, "access-key", true, true, "user");
    }

    @Test
    void previewSkipsWatermarkWhenEnabledButAdmin() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Resource resource = mock(Resource.class);
        AttachmentDownloadResource downloadResource = new AttachmentDownloadResource(
                resource, MediaType.APPLICATION_OCTET_STREAM, "inline; filename=\"test.txt\"");
        org.springframework.security.core.GrantedAuthority adminAuthority =
                mock(org.springframework.security.core.GrantedAuthority.class);

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(principal.getUsername()).thenReturn("admin");
        when(adminAuthority.getAuthority()).thenReturn("ROLE_ADMIN");
        Collection<? extends org.springframework.security.core.GrantedAuthority> adminAuthorities2 = List.of(adminAuthority);
        doReturn(adminAuthorities2).when(principal).getAuthorities();
        when(systemSwitchService.shouldWatermarkAttachments()).thenReturn(true);
        when(attachmentService.loadDownloadResource(eq(1L), eq("access-key"), eq(true), eq(false), eq("admin"))).thenReturn(downloadResource);

        ResponseEntity<Resource> response = controller.preview(principal, 1L, "sales-order", "access-key");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(attachmentService).loadDownloadResource(1L, "access-key", true, false, "admin");
    }
}
