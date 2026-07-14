package com.leo.erp.statement.customer.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerStatementRequestTest {

    @Test
    void shouldDefaultLegacyConstructorFields() {
        CustomerStatementRequest request = new CustomerStatementRequest(
                "KHDZ-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                "草稿",
                "备注",
                List.of()
        );

        assertThat(request.customerCode()).isNull();
        assertThat(request.projectId()).isNull();
        assertThat(request.settlementCompanyId()).isNull();
        assertThat(request.settlementCompanyName()).isNull();
        assertThat(request.customerName()).isEqualTo("客户A");
    }
}
