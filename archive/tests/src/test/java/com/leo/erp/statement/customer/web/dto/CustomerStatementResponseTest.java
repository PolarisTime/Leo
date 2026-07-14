package com.leo.erp.statement.customer.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerStatementResponseTest {

    @Test
    void legacyConstructorWithCustomerCodeShouldDefaultSettlementCompanyFields() {
        CustomerStatementResponse response = new CustomerStatementResponse(
                1L,
                "CS-001",
                "C001",
                "客户A",
                2L,
                "项目A",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("800.00"),
                "草稿",
                "备注",
                List.of()
        );

        assertThat(response.customerCode()).isEqualTo("C001");
        assertThat(response.projectId()).isEqualTo(2L);
        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
    }

    @Test
    void legacyConstructorWithoutCustomerCodeShouldDefaultCodeProjectAndSettlementCompanyFields() {
        CustomerStatementResponse response = new CustomerStatementResponse(
                1L,
                "CS-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("800.00"),
                "草稿",
                "备注",
                List.of()
        );

        assertThat(response.customerCode()).isNull();
        assertThat(response.projectId()).isNull();
        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.closingAmount()).isEqualByComparingTo("800.00");
    }
}
