package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentBindingRequest;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentBindingControllerTest {

    private final AttachmentWebService attachmentWebService = mock(AttachmentWebService.class);
    private final ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
    private final AttachmentRecordAccessService attachmentRecordAccessService = mock(AttachmentRecordAccessService.class);
    private final AttachmentBindingController controller = new AttachmentBindingController(
            attachmentWebService, modulePermissionGuard, attachmentRecordAccessService);

    @Test
    void detailReturnsBinding() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        AttachmentBindingResponse binding = mock(AttachmentBindingResponse.class);

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(attachmentWebService.detail(eq("sales-order"), eq(1L))).thenReturn(binding);

        ApiResponse<AttachmentBindingResponse> response = controller.detail(principal, "sales-order", 1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(binding);
    }

    @Test
    void updateReturnsUpdatedBinding() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        AttachmentBindingRequest request = new AttachmentBindingRequest("sales-order", 1L, List.of(1L, 2L));
        AttachmentBindingResponse binding = mock(AttachmentBindingResponse.class);

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "update")).thenReturn("sales-order");
        when(attachmentWebService.replace(eq("sales-order"), eq(1L), eq(List.of(1L, 2L)))).thenReturn(binding);

        ApiResponse<AttachmentBindingResponse> response = controller.update(principal, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(attachmentWebService).replace("sales-order", 1L, List.of(1L, 2L));
    }
}
