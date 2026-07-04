package com.leo.erp.statement.freight.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FreightStatementCandidateResponseTest {

    @Test
    void legacyConstructorShouldDefaultSettlementCompanyFields() {
        FreightStatementCandidateResponse response = new FreightStatementCandidateResponse(
                1L,
                "FB-001",
                "承运商A",
                "客户A",
                "项目A",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("10.000"),
                new BigDecimal("1000.00"),
                "未审核"
        );

        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.carrierName()).isEqualTo("承运商A");
        assertThat(response.totalFreight()).isEqualByComparingTo("1000.00");
    }
}
