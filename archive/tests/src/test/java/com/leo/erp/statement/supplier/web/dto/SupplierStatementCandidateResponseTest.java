package com.leo.erp.statement.supplier.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierStatementCandidateResponseTest {

    @Test
    void shouldDefaultLegacyConstructorFields() {
        SupplierStatementCandidateResponse response = new SupplierStatementCandidateResponse(
                1L,
                "RK-001",
                "供应商A",
                "仓库A",
                LocalDate.of(2026, 5, 1),
                "月结",
                new BigDecimal("12.50"),
                new BigDecimal("100.00"),
                "完成入库"
        );

        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.inboundNo()).isEqualTo("RK-001");
    }
}
