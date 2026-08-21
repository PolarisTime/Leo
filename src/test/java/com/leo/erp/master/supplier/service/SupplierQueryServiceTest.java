package com.leo.erp.master.supplier.service;

import com.leo.erp.master.api.SupplierQuery.SupplierSnapshot;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SupplierQueryService 极端情况测试：映射完整性、列表保序、查询未命中返回 empty。
 */
@ExtendWith(MockitoExtension.class)
class SupplierQueryServiceTest {

    @Mock
    private SupplierRepository repository;

    @InjectMocks
    private SupplierQueryService service;

    private Supplier supplier() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSupplierCode("S001");
        supplier.setSupplierName("供应商甲");
        return supplier;
    }

    // ---------- findActiveById ----------

    @Test
    void findActiveById_shouldMapSnapshotWhenPresent() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(supplier()));

        assertThat(service.findActiveById(1L)).contains(new SupplierSnapshot(1L, "S001", "供应商甲"));
    }

    @Test
    void findActiveById_shouldReturnEmptyWhenMissing() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        assertThat(service.findActiveById(1L)).isEmpty();
    }

    // ---------- findActiveByCode ----------

    @Test
    void findActiveByCode_shouldMapSnapshotWhenPresent() {
        when(repository.findBySupplierCodeAndDeletedFlagFalse("S001")).thenReturn(Optional.of(supplier()));

        assertThat(service.findActiveByCode("S001")).contains(new SupplierSnapshot(1L, "S001", "供应商甲"));
    }

    @Test
    void findActiveByCode_shouldReturnEmptyWhenMissing() {
        when(repository.findBySupplierCodeAndDeletedFlagFalse("S001")).thenReturn(Optional.empty());

        assertThat(service.findActiveByCode("S001")).isEmpty();
    }

    // ---------- findFirstActiveByNameOrderByCode ----------

    @Test
    void findFirstActiveByNameOrderByCode_shouldMapSnapshotWhenPresent() {
        when(repository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商甲"))
                .thenReturn(Optional.of(supplier()));

        assertThat(service.findFirstActiveByNameOrderByCode("供应商甲"))
                .contains(new SupplierSnapshot(1L, "S001", "供应商甲"));
    }

    @Test
    void findFirstActiveByNameOrderByCode_shouldReturnEmptyWhenMissing() {
        when(repository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商甲"))
                .thenReturn(Optional.empty());

        assertThat(service.findFirstActiveByNameOrderByCode("供应商甲")).isEmpty();
    }

    // ---------- findActiveByNameOrderByCode ----------

    @Test
    void findActiveByNameOrderByCode_shouldMapListPreservingOrder() {
        Supplier first = new Supplier();
        first.setId(1L);
        first.setSupplierCode("S001");
        first.setSupplierName("供应商甲");
        Supplier second = new Supplier();
        second.setId(2L);
        second.setSupplierCode("S002");
        second.setSupplierName("供应商乙");
        when(repository.findBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商"))
                .thenReturn(List.of(first, second));

        List<SupplierSnapshot> snapshots = service.findActiveByNameOrderByCode("供应商");

        assertThat(snapshots).containsExactly(
                new SupplierSnapshot(1L, "S001", "供应商甲"),
                new SupplierSnapshot(2L, "S002", "供应商乙")
        );
    }

    @Test
    void findActiveByNameOrderByCode_shouldReturnEmptyWhenNone() {
        when(repository.findBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("不存在"))
                .thenReturn(List.of());

        assertThat(service.findActiveByNameOrderByCode("不存在")).isEmpty();
    }
}
