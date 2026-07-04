package com.leo.erp.finance.receipt.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptResponseTest {

    @Test
    void shouldDefaultLegacyConstructorFields() {
        ReceiptResponse response = new ReceiptResponse(
                1L,
                "SK-001",
                "客户A",
                "项目A",
                21L,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                new BigDecimal("100.00"),
                "草稿",
                "财务A",
                "备注",
                List.of()
        );

        assertThat(response.customerCode()).isNull();
        assertThat(response.projectId()).isNull();
        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.sourceStatementId()).isEqualTo(21L);
        assertThat(response.items()).isEmpty();
    }
}
