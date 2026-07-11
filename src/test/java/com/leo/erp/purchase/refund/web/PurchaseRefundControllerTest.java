package com.leo.erp.purchase.refund.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.purchase.refund.service.PurchaseRefundService;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundPreviewResponse;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundSourceCandidateResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseRefundControllerTest {

    @Test
    void sourceCandidatesReturnsCalculatedPage() {
        PurchaseRefundService service = mock(PurchaseRefundService.class);
        PurchaseRefundController controller = new PurchaseRefundController(service);
        PurchaseRefundSourceCandidateResponse candidate = mock(PurchaseRefundSourceCandidateResponse.class);
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.sourceCandidates(org.mockito.ArgumentMatchers.eq(query), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(candidate)));

        ApiResponse<PageResponse<PurchaseRefundSourceCandidateResponse>> response = controller.sourceCandidates(
                query,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.code()).isZero();
        assertThat(response.data().content()).containsExactly(candidate);
    }

    @Test
    void previewReturnsServerCalculatedSnapshot() {
        PurchaseRefundService service = mock(PurchaseRefundService.class);
        PurchaseRefundController controller = new PurchaseRefundController(service);
        PurchaseRefundPreviewResponse preview = mock(PurchaseRefundPreviewResponse.class);
        when(service.preview(1L)).thenReturn(preview);

        ApiResponse<PurchaseRefundPreviewResponse> response = controller.preview(1L);

        assertThat(response.code()).isZero();
        assertThat(response.data()).isSameAs(preview);
        verify(service).preview(1L);
    }

    @Test
    void sourceCandidatesRequiresPurchaseOrderReadPermission() throws NoSuchMethodException {
        RequiresPermission permission = PurchaseRefundController.class
                .getMethod(
                        "sourceCandidates",
                        PageQuery.class,
                        String.class,
                        String.class,
                        Long.class,
                        LocalDate.class,
                        LocalDate.class
                )
                .getAnnotation(RequiresPermission.class);

        assertThat(permission.resource()).isEqualTo("purchase-order");
        assertThat(permission.action()).isEqualTo("read");
    }

    @Test
    void previewRequiresPurchaseOrderReadPermission() throws NoSuchMethodException {
        RequiresPermission permission = PurchaseRefundController.class
                .getMethod("preview", Long.class)
                .getAnnotation(RequiresPermission.class);

        assertThat(permission.resource()).isEqualTo("purchase-order");
        assertThat(permission.action()).isEqualTo("read");
    }
}
