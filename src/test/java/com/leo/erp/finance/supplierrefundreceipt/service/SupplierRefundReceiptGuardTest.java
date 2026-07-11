package com.leo.erp.finance.supplierrefundreceipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.supplierrefundreceipt.repository.SupplierRefundReceiptRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierRefundReceiptGuardTest {

    @Test
    void shouldRejectPurchaseRefundMutationWhenAnyActiveReceiptExists() {
        SupplierRefundReceiptRepository repository = mock(SupplierRefundReceiptRepository.class);
        when(repository.existsByPurchaseRefundIdAndDeletedFlagFalse(91L)).thenReturn(true);
        SupplierRefundReceiptGuard guard = new SupplierRefundReceiptGuard(repository);

        assertThatThrownBy(() -> guard.assertNoActiveReceipt(91L, "反审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商退款到账单")
                .hasMessageContaining("不能反审核");
    }
}
