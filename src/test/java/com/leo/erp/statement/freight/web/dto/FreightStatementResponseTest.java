package com.leo.erp.statement.freight.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreightStatementResponseTest {

    @Test
    void legacyConstructorWithCarrierCodeShouldDefaultSettlementCompanyFields() {
        FreightStatementResponse response = new FreightStatementResponse(
                1L,
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
                List.of(),
                "备注",
                List.of()
        );

        assertThat(response.carrierCode()).isEqualTo("C001");
        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.unpaidAmount()).isEqualByComparingTo("800.00");
    }

    @Test
    void legacyConstructorWithoutCarrierCodeShouldDefaultCarrierAndSettlementCompanyFields() {
        FreightStatementResponse response = new FreightStatementResponse(
                1L,
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
                List.of(),
                "备注",
                List.of()
        );

        assertThat(response.carrierCode()).isNull();
        assertThat(response.carrierName()).isEqualTo("承运商A");
        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
    }
}
