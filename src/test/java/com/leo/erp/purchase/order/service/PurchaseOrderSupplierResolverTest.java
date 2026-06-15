package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderSupplierResolverTest {

    @Test
    void shouldReturnMasterSupplierName() {
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        Supplier supplier = new Supplier();
        supplier.setSupplierName("供应商A");
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier));

        PurchaseOrderSupplierResolver resolver = new PurchaseOrderSupplierResolver(supplierRepository);

        assertThat(resolver.requireMasterSupplierName(" 供应商A ")).isEqualTo("供应商A");
    }

    @Test
    void shouldRejectMissingSupplier() {
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.empty());

        PurchaseOrderSupplierResolver resolver = new PurchaseOrderSupplierResolver(supplierRepository);

        assertThatThrownBy(() -> resolver.requireMasterSupplierName("供应商A"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在，请先在主数据供应商资料中维护");
    }
}
