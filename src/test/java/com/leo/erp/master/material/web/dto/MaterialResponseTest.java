package com.leo.erp.master.material.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialResponseTest {

    @Test
    void recordAccessors() {
        MaterialResponse response = new MaterialResponse(
                1L, "M001", "品牌A", "钢材", "板材", "10mm", "6m", "吨", "件",
                new BigDecimal("0.500"), 10, new BigDecimal("5000.00"), true, "备注"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.materialCode()).isEqualTo("M001");
        assertThat(response.brand()).isEqualTo("品牌A");
        assertThat(response.material()).isEqualTo("钢材");
        assertThat(response.category()).isEqualTo("板材");
        assertThat(response.spec()).isEqualTo("10mm");
        assertThat(response.length()).isEqualTo("6m");
        assertThat(response.unit()).isEqualTo("吨");
        assertThat(response.quantityUnit()).isEqualTo("件");
        assertThat(response.pieceWeightTon()).isEqualByComparingTo("0.500");
        assertThat(response.piecesPerBundle()).isEqualTo(10);
        assertThat(response.unitPrice()).isEqualByComparingTo("5000.00");
        assertThat(response.batchNoEnabled()).isTrue();
        assertThat(response.remark()).isEqualTo("备注");
    }

    @Test
    void recordEquality() {
        MaterialResponse a = new MaterialResponse(
                1L, "M001", "品牌A", "钢材", "板材", "10mm", "6m", "吨", "件",
                new BigDecimal("0.500"), 10, new BigDecimal("5000.00"), true, "备注"
        );
        MaterialResponse b = new MaterialResponse(
                1L, "M001", "品牌A", "钢材", "板材", "10mm", "6m", "吨", "件",
                new BigDecimal("0.500"), 10, new BigDecimal("5000.00"), true, "备注"
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        MaterialResponse response = new MaterialResponse(
                1L, "M001", "品牌A", "钢材", "板材", "10mm", "6m", "吨", "件",
                new BigDecimal("0.500"), 10, new BigDecimal("5000.00"), true, "备注"
        );
        assertThat(response.toString()).contains("M001", "品牌A");
    }
}