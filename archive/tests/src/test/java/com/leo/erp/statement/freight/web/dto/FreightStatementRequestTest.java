package com.leo.erp.statement.freight.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreightStatementRequestTest {

    @Test
    void legacyConstructorWithoutCarrierCodeShouldDefaultOptionalFields() {
        FreightStatementRequest request = new FreightStatementRequest(
                "FS-001",
                "承运商A",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                new BigDecimal("10.000"),
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("800.00"),
                "草稿",
                "未签署",
                "att",
                "备注",
                List.of()
        );

        assertThat(request.carrierCode()).isNull();
        assertThat(request.settlementCompanyId()).isNull();
        assertThat(request.settlementCompanyName()).isNull();
        assertThat(request.carrierName()).isEqualTo("承运商A");
    }

    @Test
    void legacyConstructorWithCarrierCodeShouldDefaultSettlementCompanyFields() {
        FreightStatementRequest request = new FreightStatementRequest(
                "FS-001",
                "C001",
                "承运商A",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                new BigDecimal("10.000"),
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("800.00"),
                "草稿",
                "未签署",
                "att",
                "备注",
                List.of()
        );

        assertThat(request.carrierCode()).isEqualTo("C001");
        assertThat(request.settlementCompanyId()).isNull();
        assertThat(request.settlementCompanyName()).isNull();
        assertThat(request.unpaidAmount()).isEqualByComparingTo("800.00");
    }
}
