package com.leo.erp.logistics.bill.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillItemResponseTest {

    @Test
    void legacyConstructorShouldDefaultSourceAndSettlementCompanyFields() {
        FreightBillItemResponse response = new FreightBillItemResponse(
                1L,
                2,
                "OUT-001",
                "客户A",
                "项目A",
                "M1",
                "螺纹钢 HRB400",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "9m",
                3,
                "支",
                new BigDecimal("0.400"),
                1,
                "B1",
                new BigDecimal("1.200"),
                "一号库"
        );

        assertThat(response.sourceSalesOutboundItemId()).isNull();
        assertThat(response.sourceSalesOutboundStatus()).isNull();
        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.weightTon()).isEqualByComparingTo("1.200");
    }
}
