package com.leo.erp.finance.payment.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.finance.payment.service.PaymentPrepaymentAllocationService;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.finance.payment.web.dto.PaymentPrepaymentAllocationUpdateRequest;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentPrepaymentAllocationControllerTest {

    @Test
    void shouldReplacePaidPurchasePrepaymentAllocationsThroughDedicatedEndpoint() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentPrepaymentAllocationService allocationService = mock(PaymentPrepaymentAllocationService.class);
        PaymentController controller = new PaymentController(paymentService, allocationService);
        PaymentPrepaymentAllocationUpdateRequest request = new PaymentPrepaymentAllocationUpdateRequest(List.of(
                new PaymentAllocationRequest(null, 11L, new BigDecimal("200.00"))
        ));
        List<PaymentAllocationResponse> expected = List.of(mock(PaymentAllocationResponse.class));
        when(allocationService.replaceAllocations(5L, request)).thenReturn(expected);

        ApiResponse<List<PaymentAllocationResponse>> response = controller.updatePrepaymentAllocations(5L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("采购预付款核销明细更新成功");
        assertThat(response.data()).isSameAs(expected);
        verify(allocationService).replaceAllocations(5L, request);
    }

    @Test
    void shouldExposeDedicatedPutEndpointWithPaymentUpdatePermissionAndValidation() throws NoSuchMethodException {
        Method method = PaymentController.class.getMethod(
                "updatePrepaymentAllocations",
                Long.class,
                PaymentPrepaymentAllocationUpdateRequest.class
        );

        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        RequiresPermission permission = method.getAnnotation(RequiresPermission.class);
        assertThat(putMapping.value()).containsExactly("/{id}/prepayment-allocations");
        assertThat(permission.resource()).isEqualTo("payment");
        assertThat(permission.action()).isEqualTo("update");
        assertThat(method.getParameters()[1].isAnnotationPresent(Valid.class)).isTrue();
        assertThat(method.getParameters()[1].isAnnotationPresent(RequestBody.class)).isTrue();
    }
}
