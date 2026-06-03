package com.leo.erp.master.material.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialCategoryOptionResponseTest {

    @Test
    void shouldCreateRecord() {
        MaterialCategoryOptionResponse response = new MaterialCategoryOptionResponse(
                "C001", "钢材", true
        );

        assertThat(response.value()).isEqualTo("C001");
        assertThat(response.label()).isEqualTo("钢材");
        assertThat(response.purchaseWeighRequired()).isTrue();
    }

    @Test
    void shouldSupportNullValues() {
        MaterialCategoryOptionResponse response = new MaterialCategoryOptionResponse(
                null, null, null
        );

        assertThat(response.value()).isNull();
        assertThat(response.label()).isNull();
        assertThat(response.purchaseWeighRequired()).isNull();
    }

    @Test
    void shouldImplementEquals() {
        MaterialCategoryOptionResponse r1 = new MaterialCategoryOptionResponse("C001", "钢材", true);
        MaterialCategoryOptionResponse r2 = new MaterialCategoryOptionResponse("C001", "钢材", true);

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void shouldImplementHashCode() {
        MaterialCategoryOptionResponse r1 = new MaterialCategoryOptionResponse("C001", "钢材", true);
        MaterialCategoryOptionResponse r2 = new MaterialCategoryOptionResponse("C001", "钢材", true);

        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
