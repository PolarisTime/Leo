package com.leo.erp.statement.supplier.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierStatementItemRequestTest {

    @Test
    void legacyConstructorWithIdShouldDefaultWeightAdjustmentFields() {
        SupplierStatementItemRequest request = new SupplierStatementItemRequest(
                1L,
                "IN-001",
                101L,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "9m",
                "吨",
                "B1",
                3,
                "支",
                new BigDecimal("0.400"),
                1,
                new BigDecimal("1.200"),
                new BigDecimal("4000.00"),
                new BigDecimal("4800.00")
        );

        assertThat(request.id()).isEqualTo(1L);
        assertThat(request.weighWeightTon()).isNull();
        assertThat(request.weightAdjustmentTon()).isNull();
        assertThat(request.weightAdjustmentAmount()).isNull();
    }

    @Test
    void legacyConstructorWithoutIdShouldDefaultIdAndWeightAdjustmentFields() {
        SupplierStatementItemRequest request = new SupplierStatementItemRequest(
                "IN-001",
                101L,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "9m",
                "吨",
                "B1",
                3,
                "支",
                new BigDecimal("0.400"),
                1,
                new BigDecimal("1.200"),
                new BigDecimal("4000.00"),
                new BigDecimal("4800.00")
        );

        assertThat(request.id()).isNull();
        assertThat(request.sourceNo()).isEqualTo("IN-001");
        assertThat(request.weighWeightTon()).isNull();
        assertThat(request.weightAdjustmentTon()).isNull();
        assertThat(request.weightAdjustmentAmount()).isNull();
    }
}
