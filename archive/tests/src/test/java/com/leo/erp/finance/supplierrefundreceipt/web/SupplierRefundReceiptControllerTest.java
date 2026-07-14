package com.leo.erp.finance.supplierrefundreceipt.web;

import com.leo.erp.finance.supplierrefundreceipt.service.SupplierRefundReceiptService;
import com.leo.erp.security.permission.RequiresPermission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SupplierRefundReceiptControllerTest {

    @Test
    void shouldExposeReadOnlyHistoryEndpointsOnly() throws NoSuchMethodException {
        SupplierRefundReceiptController controller = new SupplierRefundReceiptController(
                mock(SupplierRefundReceiptService.class)
        );

        RequiresPermission permission = controller.getClass()
                .getMethod("detail", Long.class)
                .getAnnotation(RequiresPermission.class);

        assertThat(permission.resource()).isEqualTo("supplier-refund-receipt");
        assertThat(permission.action()).isEqualTo("read");
        assertThatThrownBy(() -> controller.getClass().getMethod("create", Object.class))
                .isInstanceOf(NoSuchMethodException.class);
    }
}
