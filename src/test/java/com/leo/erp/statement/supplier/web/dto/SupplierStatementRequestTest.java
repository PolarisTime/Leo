package com.leo.erp.statement.supplier.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierStatementRequestTest {

    @Test
    void shouldDefaultLegacyConstructorFields() {
        SupplierStatementRequest request = new SupplierStatementRequest(
                "GYSDZ-001",
                "S-001",
                "供应商A",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                "草稿",
                "备注",
                List.of()
        );

        assertThat(request.supplierCode()).isEqualTo("S-001");
        assertThat(request.supplierName()).isEqualTo("供应商A");
        assertThat(request.settlementCompanyId()).isNull();
        assertThat(request.settlementCompanyName()).isNull();
    }
}
