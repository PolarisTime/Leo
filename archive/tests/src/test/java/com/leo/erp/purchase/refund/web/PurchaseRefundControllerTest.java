package com.leo.erp.purchase.refund.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.refund.service.PurchaseRefundService;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseRefundControllerTest {

    @Test
    void exposesOnlyReadAndAuditWorkflowMethods() {
        assertThat(Arrays.stream(PurchaseRefundController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .contains("search", "page", "detail", "updateStatus")
                .doesNotContain("create", "update", "delete", "preview", "sourceCandidates");
    }

    @Test
    void updateStatusUsesAuditPermissionAndDelegatesToService() throws NoSuchMethodException {
        PurchaseRefundService service = mock(PurchaseRefundService.class);
        PurchaseRefundController controller = new PurchaseRefundController(service);
        PurchaseRefundResponse response = mock(PurchaseRefundResponse.class);
        when(service.updateStatus(1L, "已审核")).thenReturn(response);

        ApiResponse<PurchaseRefundResponse> result = controller.updateStatus(
                1L,
                new StatusUpdateRequest("已审核")
        );

        RequiresPermission permission = PurchaseRefundController.class
                .getMethod("updateStatus", Long.class, StatusUpdateRequest.class)
                .getAnnotation(RequiresPermission.class);
        assertThat(permission.resource()).isEqualTo("purchase-refund");
        assertThat(permission.action()).isEqualTo("audit");
        assertThat(result.data()).isSameAs(response);
        verify(service).updateStatus(1L, "已审核");
    }
}
