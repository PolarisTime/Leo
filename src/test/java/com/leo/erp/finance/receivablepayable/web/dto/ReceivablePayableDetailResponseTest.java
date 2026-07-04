package com.leo.erp.finance.receivablepayable.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivablePayableDetailResponseTest {

    @Test
    void shouldDefaultLegacyConstructorFields() {
        ReceivablePayableDetailResponse response = new ReceivablePayableDetailResponse(
                "id-1",
                "应收",
                "客户",
                "客户A",
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2L,
                "未结清",
                "备注",
                List.of()
        );

        assertThat(response.counterpartyCode()).isNull();
        assertThat(response.counterpartyName()).isEqualTo("客户A");
        assertThat(response.reconciliationStatus()).isEqualTo("未对账");
        assertThat(response.items()).isEmpty();
    }
}
