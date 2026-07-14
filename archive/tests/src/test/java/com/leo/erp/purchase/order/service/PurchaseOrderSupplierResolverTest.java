package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
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

    @Test
    void shouldNormalizeNullSupplierNameToEmptyText() {
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(""))
                .thenReturn(Optional.empty());

        PurchaseOrderSupplierResolver resolver = new PurchaseOrderSupplierResolver(supplierRepository);

        assertThatThrownBy(() -> resolver.requireMasterSupplierName(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在，请先在主数据供应商资料中维护");
    }

    @Test
    void shouldResolveStableIdentityBySupplierCode() {
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        Supplier supplier = supplier("SUP-001", "供应商A-主数据新名称");
        when(supplierRepository.findBySupplierCodeAndDeletedFlagFalse("SUP-001"))
                .thenReturn(Optional.of(supplier));

        PurchaseOrderSupplierResolver resolver = new PurchaseOrderSupplierResolver(supplierRepository);

        PurchaseOrderSupplierResolver.SupplierIdentity identity =
                resolver.requireMasterSupplier(" SUP-001 ", "供应商A-旧名称");

        assertThat(identity.supplierCode()).isEqualTo("SUP-001");
        assertThat(identity.supplierName()).isEqualTo("供应商A-主数据新名称");
    }

    @Test
    void shouldRejectAmbiguousSupplierNameWhenCodeIsMissing() {
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        when(supplierRepository.findBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("同名供应商"))
                .thenReturn(List.of(
                        supplier("SUP-001", "同名供应商"),
                        supplier("SUP-002", "同名供应商")
                ));

        PurchaseOrderSupplierResolver resolver = new PurchaseOrderSupplierResolver(supplierRepository);

        assertThatThrownBy(() -> resolver.requireMasterSupplier(null, "同名供应商"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商名称对应多个编码");
    }

    @Test
    void shouldResolveSupplierByStableIdAndReturnAuthoritativeSnapshot() throws ReflectiveOperationException {
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        Supplier supplier = supplier("SUP-001", "供应商A");
        supplier.setId(501L);
        when(supplierRepository.findByIdAndDeletedFlagFalse(501L)).thenReturn(Optional.of(supplier));
        PurchaseOrderSupplierResolver resolver = new PurchaseOrderSupplierResolver(supplierRepository);

        Optional<Method> resolverMethod = Arrays.stream(PurchaseOrderSupplierResolver.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("requireMasterSupplier"))
                .filter(method -> Arrays.equals(
                        method.getParameterTypes(),
                        new Class<?>[]{Long.class, String.class, String.class}
                ))
                .findFirst();

        assertThat(resolverMethod).as("采购订单供应商解析器必须接收 supplierId").isPresent();
        if (resolverMethod.isEmpty()) {
            return;
        }
        resolverMethod.get().setAccessible(true);
        Object identity = resolverMethod.get().invoke(resolver, 501L, "SUP-001", "供应商A");
        Method supplierIdAccessor = identity.getClass().getDeclaredMethod("supplierId");
        supplierIdAccessor.setAccessible(true);

        assertThat(supplierIdAccessor.invoke(identity)).isEqualTo(501L);
    }

    private Supplier supplier(String supplierCode, String supplierName) {
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(supplierCode);
        supplier.setSupplierName(supplierName);
        return supplier;
    }
}
