package com.leo.erp.statement.customer.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerStatementCandidateResponseTest {

    @Test
    void shouldDefaultLegacyConstructorFields() {
        CustomerStatementCandidateResponse response = new CustomerStatementCandidateResponse(
                1L,
                "SO-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 5, 1),
                "销售A",
                new BigDecimal("12.50"),
                new BigDecimal("100.00"),
                "完成销售"
        );

        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.orderNo()).isEqualTo("SO-001");
    }
}
