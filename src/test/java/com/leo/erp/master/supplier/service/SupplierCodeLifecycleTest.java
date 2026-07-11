package com.leo.erp.master.supplier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.mapper.SupplierMapper;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierCodeLifecycleTest {

    @Test
    void shouldRejectSupplierCodeChangeWhenStableBusinessSnapshotReferencesOldCode() {
        List<String> stableReferenceTables = List.of(
                "po_purchase_order",
                "po_purchase_inbound",
                "po_purchase_refund",
                "fm_invoice_receipt"
        );

        for (String referencedTable : stableReferenceTables) {
            SupplierRepository repository = mock(SupplierRepository.class);
            SupplierMapper mapper = mock(SupplierMapper.class);
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            Supplier existing = supplier("SUP-001", "供应商旧名称");
            when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
            when(repository.existsBySupplierCodeAndDeletedFlagFalse("SUP-002")).thenReturn(false);
            when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenAnswer(invocation -> {
                        String sql = invocation.getArgument(0);
                        return sql.contains("FROM " + referencedTable) ? 1L : 0L;
                    });

            SupplierService service = new SupplierService(
                    repository,
                    mock(SnowflakeIdGenerator.class),
                    mapper,
                    null,
                    new MasterDataReferenceGuard(jdbc)
            );
            SupplierRequest request = new SupplierRequest(
                    "SUP-002",
                    "供应商新名称",
                    null,
                    null,
                    null,
                    StatusConstants.NORMAL,
                    null
            );

            assertThatThrownBy(() -> service.update(1L, request))
                    .as("稳定引用表 %s 应锁定供应商编码", referencedTable)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("供应商编码")
                    .hasMessageContaining("不能修改");
            verify(repository, never()).save(any());
        }
    }

    @Test
    void shouldAllowSupplierNameChangeWhenSupplierCodeIsUnchanged() {
        SupplierRepository repository = mock(SupplierRepository.class);
        SupplierMapper mapper = mock(SupplierMapper.class);
        MasterDataReferenceGuard referenceGuard = mock(MasterDataReferenceGuard.class);
        Supplier existing = supplier("SUP-001", "供应商旧名称");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenAnswer(invocation -> response(existing));

        SupplierService service = new SupplierService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                null,
                referenceGuard
        );
        SupplierRequest request = new SupplierRequest(
                "SUP-001",
                "供应商新名称",
                null,
                null,
                null,
                StatusConstants.NORMAL,
                null
        );

        SupplierResponse result = service.update(1L, request);

        assertThat(result.supplierName()).isEqualTo("供应商新名称");
        verify(referenceGuard, never()).assertNoReferences(anyString(), any());
    }

    private Supplier supplier(String supplierCode, String supplierName) {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSupplierCode(supplierCode);
        supplier.setSupplierName(supplierName);
        supplier.setStatus(StatusConstants.NORMAL);
        return supplier;
    }

    private SupplierResponse response(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getSupplierName(),
                supplier.getContactName(),
                supplier.getContactPhone(),
                supplier.getCity(),
                supplier.getStatus(),
                supplier.getRemark()
        );
    }
}
