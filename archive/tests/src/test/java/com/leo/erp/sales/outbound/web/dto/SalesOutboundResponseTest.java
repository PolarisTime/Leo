package com.leo.erp.sales.outbound.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOutboundResponseTest {

    @Test
    void shouldCreateWithAllFields() {
        SalesOutboundResponse response = new SalesOutboundResponse(
                1L, "SOO-001", "SO-001", "客户A", "项目A", "一号库",
                LocalDate.of(2026, 5, 1),
                new BigDecimal("10.500"), new BigDecimal("31500.00"),
                "草稿", "备注", List.of()
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.outboundNo()).isEqualTo("SOO-001");
        assertThat(response.salesOrderNo()).isEqualTo("SO-001");
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.warehouseName()).isEqualTo("一号库");
        assertThat(response.outboundDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.totalWeight()).isEqualByComparingTo("10.500");
        assertThat(response.totalAmount()).isEqualByComparingTo("31500.00");
        assertThat(response.status()).isEqualTo("草稿");
        assertThat(response.deletedFlag()).isFalse();
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.items()).isEmpty();
    }
}
