package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentBindingRequest;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.attachment.web.dto.AttachmentBindingCountResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void countsReturnsBindingCounts() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        AttachmentBindingCountResponse counts = new AttachmentBindingCountResponse(
                "sales-order",
                Map.of(1L, 2, 2L, 0)
        );

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(attachmentWebService.counts(eq("sales-order"), eq(List.of(1L, 2L)))).thenReturn(counts);

        ApiResponse<AttachmentBindingCountResponse> response = controller.counts(principal, "sales-order", "1,2");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(counts);
        verify(attachmentRecordAccessService).assertRecordAccessible(principal, "sales-order", "read", 1L);
        verify(attachmentRecordAccessService).assertRecordAccessible(principal, "sales-order", "read", 2L);
        verify(attachmentWebService).counts("sales-order", List.of(1L, 2L));
    }

    @Test
    void countsFiltersZeroRecordIdBeforeAccessCheck() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        AttachmentBindingCountResponse counts = new AttachmentBindingCountResponse("sales-order", Map.of(7L, 1));

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(attachmentWebService.counts(eq("sales-order"), eq(List.of(7L)))).thenReturn(counts);

        ApiResponse<AttachmentBindingCountResponse> response = controller.counts(principal, "sales-order", "0,7,0");

        assertThat(response.data()).isEqualTo(counts);
        verify(attachmentRecordAccessService).assertRecordAccessible(principal, "sales-order", "read", 7L);
        verify(attachmentWebService).counts("sales-order", List.of(7L));
    }

    @Test
    void countsSkipsMissingRecordsDuringDeleteRace() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        AttachmentBindingCountResponse counts = new AttachmentBindingCountResponse("sales-order", Map.of(1L, 2));

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        doThrow(new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在"))
                .when(attachmentRecordAccessService)
                .assertRecordAccessible(principal, "sales-order", "read", 2L);
        when(attachmentWebService.counts(eq("sales-order"), eq(List.of(1L)))).thenReturn(counts);

        ApiResponse<AttachmentBindingCountResponse> response = controller.counts(principal, "sales-order", "1,2");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(counts);
        verify(attachmentRecordAccessService).assertRecordAccessible(principal, "sales-order", "read", 1L);
        verify(attachmentRecordAccessService).assertRecordAccessible(principal, "sales-order", "read", 2L);
        verify(attachmentWebService).counts("sales-order", List.of(1L));
    }
}
