package com.leo.erp.finance.payment.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentController controller = new PaymentController(paymentService);

    @Test
    void searchReturnsPaymentList() {
        PaymentResponse payment = mock(PaymentResponse.class);
        when(paymentService.search("test", 100)).thenReturn(List.of(payment));

        ApiResponse<List<PaymentResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(payment);
        verify(paymentService).search("test", 100);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        when(paymentService.search("", 100)).thenReturn(List.of());

        ApiResponse<List<PaymentResponse>> response = controller.search(null, 100);

        assertThat(response.data()).isEmpty();
        verify(paymentService).search("", 100);
    }

    @Test
    void searchLimitsMaxTo500() {
        when(paymentService.search("test", 500)).thenReturn(List.of());

        controller.search("test", 1000);

        verify(paymentService).search("test", 500);
    }

    @Test
    void pageReturnsPaginatedPayments() {
        PaymentResponse payment = mock(PaymentResponse.class);
        Page<PaymentResponse> page = new PageImpl<>(List.of(payment));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(paymentService.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<PaymentResponse>> response = controller.page(query, "test", "business", "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsPaymentById() {
        PaymentResponse payment = mock(PaymentResponse.class);
        when(paymentService.detail(1L)).thenReturn(payment);

        ApiResponse<PaymentResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(payment);
    }

    @Test
    void createReturnsCreatedPayment() {
        PaymentRequest request = mock(PaymentRequest.class);
        PaymentResponse created = mock(PaymentResponse.class);
        when(paymentService.create(request)).thenReturn(created);

        ApiResponse<PaymentResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(paymentService).create(request);
    }

    @Test
    void updateReturnsUpdatedPayment() {
        PaymentRequest request = mock(PaymentRequest.class);
        PaymentResponse updated = mock(PaymentResponse.class);
        when(paymentService.update(1L, request)).thenReturn(updated);

        ApiResponse<PaymentResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(paymentService).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedPayment() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        PaymentResponse updated = mock(PaymentResponse.class);
        when(paymentService.updateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<PaymentResponse> response = controller.updateStatus(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("状态更新成功");
        verify(paymentService).updateStatus(1L, "approved");
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(paymentService).delete(1L);
    }
}