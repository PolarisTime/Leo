package com.leo.erp.master.material.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialCategoryOptionResponseTest {

    @Test
    void shouldCreateRecord() {
        MaterialCategoryOptionResponse response = new MaterialCategoryOptionResponse(
                "C001", "钢材", true, new BigDecimal("3.00"), new BigDecimal("4.00")
        );

        assertThat(response.value()).isEqualTo("C001");
        assertThat(response.label()).isEqualTo("钢材");
        assertThat(response.purchaseWeighRequired()).isTrue();
        assertThat(response.purchaseWeighOverTolerancePercent()).isEqualByComparingTo("3.00");
        assertThat(response.purchaseWeighUnderTolerancePercent()).isEqualByComparingTo("4.00");
    }

    @Test
    void shouldSupportNullValues() {
        MaterialCategoryOptionResponse response = new MaterialCategoryOptionResponse(
                null, null, null, null, null
        );

        assertThat(response.value()).isNull();
        assertThat(response.label()).isNull();
        assertThat(response.purchaseWeighRequired()).isNull();
    }

    @Test
    void shouldImplementEquals() {
        MaterialCategoryOptionResponse r1 = new MaterialCategoryOptionResponse("C001", "钢材", true, null, null);
        MaterialCategoryOptionResponse r2 = new MaterialCategoryOptionResponse("C001", "钢材", true, null, null);

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void shouldImplementHashCode() {
        MaterialCategoryOptionResponse r1 = new MaterialCategoryOptionResponse("C001", "钢材", true, null, null);
        MaterialCategoryOptionResponse r2 = new MaterialCategoryOptionResponse("C001", "钢材", true, null, null);

        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
