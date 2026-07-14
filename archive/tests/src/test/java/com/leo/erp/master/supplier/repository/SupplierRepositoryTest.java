package com.leo.erp.master.supplier.repository;

import com.leo.erp.master.supplier.domain.entity.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierRepositoryTest {

    @Mock
    private SupplierRepository repository;

    @Test
    void existsBySupplierCodeAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsBySupplierCodeAndDeletedFlagFalse("S001")).thenReturn(true);

        boolean result = repository.existsBySupplierCodeAndDeletedFlagFalse("S001");

        assertThat(result).isTrue();
    }

    @Test
    void existsBySupplierCodeAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsBySupplierCodeAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsBySupplierCodeAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc_shouldReturnFirstMatch() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSupplierCode("S001");
        supplier.setSupplierName("测试供应商");
        supplier.setDeletedFlag(false);

        when(repository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("测试供应商"))
                .thenReturn(Optional.of(supplier));

        Optional<Supplier> result = repository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("测试供应商");

        assertThat(result).isPresent();
        assertThat(result.get().getSupplierCode()).isEqualTo("S001");
    }

    @Test
    void findByDeletedFlagFalseOrderBySupplierCodeAsc_shouldReturnNonDeletedSuppliers() {
        Supplier supplier1 = new Supplier();
        supplier1.setId(1L);
        supplier1.setSupplierCode("S001");
        supplier1.setSupplierName("供应商A");
        supplier1.setDeletedFlag(false);

        Supplier supplier2 = new Supplier();
        supplier2.setId(2L);
        supplier2.setSupplierCode("S002");
        supplier2.setSupplierName("供应商B");
        supplier2.setDeletedFlag(false);

        when(repository.findByDeletedFlagFalseOrderBySupplierCodeAsc()).thenReturn(List.of(supplier1, supplier2));

        List<Supplier> result = repository.findByDeletedFlagFalseOrderBySupplierCodeAsc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSupplierCode()).isEqualTo("S001");
        assertThat(result.get(1).getSupplierCode()).isEqualTo("S002");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnSupplierWhenExistsAndNotDeleted() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSupplierCode("S001");
        supplier.setSupplierName("测试供应商");
        supplier.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(supplier));

        Optional<Supplier> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getSupplierCode()).isEqualTo("S001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<Supplier> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void countByDeletedFlagFalse_shouldReturnCountOfNonDeletedSuppliers() {
        when(repository.countByDeletedFlagFalse()).thenReturn(2L);

        long count = repository.countByDeletedFlagFalse();

        assertThat(count).isEqualTo(2);
    }
}
