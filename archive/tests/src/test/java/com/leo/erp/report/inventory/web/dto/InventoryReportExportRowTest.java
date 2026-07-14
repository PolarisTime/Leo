package com.leo.erp.report.inventory.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReportExportRowTest {

    @Test
    void fromCopiesAllFieldsFromResponse() {
        InventoryReportResponse response = new InventoryReportResponse(
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
                new BigDecimal("5.250"),
                "吨",
                new BigDecimal("0.525")
        );

        InventoryReportExportRow row = InventoryReportExportRow.from(response);

        assertThat(row.materialCode()).isEqualTo("M-001");
        assertThat(row.brand()).isEqualTo("品牌A");
        assertThat(row.material()).isEqualTo("材质A");
        assertThat(row.category()).isEqualTo("类别A");
        assertThat(row.spec()).isEqualTo("规格A");
        assertThat(row.length()).isEqualTo("9m");
        assertThat(row.warehouseName()).isEqualTo("一号仓");
        assertThat(row.batchNo()).isEqualTo("B-001");
        assertThat(row.quantity()).isEqualTo(10);
        assertThat(row.quantityUnit()).isEqualTo("件");
        assertThat(row.weightTon()).isEqualByComparingTo("5.250");
        assertThat(row.pieceWeightTon()).isEqualByComparingTo("0.525");
        assertThat(row.unit()).isEqualTo("吨");
    }

    @Test
    void fromHandlesNullPieceWeightTon() {
        InventoryReportResponse response = new InventoryReportResponse(
                2L,
                "M-002",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                BigDecimal.ZERO,
                null,
                null
        );

        InventoryReportExportRow row = InventoryReportExportRow.from(response);

        assertThat(row.materialCode()).isEqualTo("M-002");
        assertThat(row.brand()).isNull();
        assertThat(row.pieceWeightTon()).isNull();
        assertThat(row.weightTon()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
