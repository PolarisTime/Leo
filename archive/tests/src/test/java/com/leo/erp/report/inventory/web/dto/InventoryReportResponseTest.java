package com.leo.erp.report.inventory.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryReportResponseTest {

    @Test
    void shouldUseEmptyItems_whenItemsIsNull() {
        InventoryReportResponse response = response(null);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void shouldCopyItemsDefensively() {
        ArrayList<InventoryReportItemResponse> items = new ArrayList<>();
        items.add(item("1", "B-001"));

        InventoryReportResponse response = response(items);
        items.clear();

        assertThat(response.items()).hasSize(1);
        assertThatThrownBy(() -> response.items().add(item("2", "B-002")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private InventoryReportResponse response(java.util.List<InventoryReportItemResponse> items) {
        return new InventoryReportResponse(
                1L,
                "M-001",
                "品牌A",
                "材质A",
                "类别A",
                "规格A",
                "9m",
                "一号仓",
                "B-001",
                10,
                "件",
                BigDecimal.ONE,
                "吨",
                BigDecimal.TEN,
                items
        );
    }

    private InventoryReportItemResponse item(String id, String batchNo) {
        return new InventoryReportItemResponse(
                id,
                "M-001",
                "品牌A",
                "材质A",
                "类别A",
                "规格A",
                "9m",
                "一号仓",
                batchNo,
                "SO-001",
                "2026-07-04",
                1,
                "件",
                BigDecimal.ONE,
                "吨",
                BigDecimal.TEN
        );
    }
}
