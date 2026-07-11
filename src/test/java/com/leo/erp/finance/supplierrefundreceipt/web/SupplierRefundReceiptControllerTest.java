package com.leo.erp.finance.supplierrefundreceipt.web;

import com.leo.erp.finance.supplierrefundreceipt.service.SupplierRefundReceiptService;
import com.leo.erp.finance.supplierrefundreceipt.web.dto.SupplierRefundReceiptRequest;
import com.leo.erp.security.permission.RequiresPermission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SupplierRefundReceiptControllerTest {

    @Test
    void createShouldUseDedicatedSupplierRefundReceiptPermission() throws NoSuchMethodException {
        SupplierRefundReceiptController controller = new SupplierRefundReceiptController(
                mock(SupplierRefundReceiptService.class)
        );

        RequiresPermission permission = controller.getClass()
                .getMethod("create", SupplierRefundReceiptRequest.class)
                .getAnnotation(RequiresPermission.class);

        assertThat(permission.resource()).isEqualTo("supplier-refund-receipt");
        assertThat(permission.action()).isEqualTo("create");
    }
}
