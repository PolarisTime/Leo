package com.leo.erp.logistics.bill.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillResponseTest {

    @Test
    void legacyConstructorShouldDefaultSettlementCompanyFields() {
        FreightBillResponse response = new FreightBillResponse(
                1L,
                "FB-001",
                "物流甲",
                "苏A12345",
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("20.00"),
                new BigDecimal("10.000"),
                new BigDecimal("200.00"),
                "未审核",
                "备注",
                List.of()
        );

        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.vehiclePlate()).isEqualTo("苏A12345");
        assertThat(response.totalFreight()).isEqualByComparingTo("200.00");
    }
}
