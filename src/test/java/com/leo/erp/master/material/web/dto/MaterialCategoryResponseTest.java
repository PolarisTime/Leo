package com.leo.erp.master.material.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialCategoryResponseTest {

    @Test
    void recordAccessors() {
        MaterialCategoryResponse response = new MaterialCategoryResponse(
                1L, "CAT001", "板材", 1, true, "启用", "备注"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.categoryCode()).isEqualTo("CAT001");
        assertThat(response.categoryName()).isEqualTo("板材");
        assertThat(response.sortOrder()).isEqualTo(1);
        assertThat(response.purchaseWeighRequired()).isTrue();
        assertThat(response.status()).isEqualTo("启用");
        assertThat(response.remark()).isEqualTo("备注");
    }

    @Test
    void recordEquality() {
        MaterialCategoryResponse a = new MaterialCategoryResponse(
                1L, "CAT001", "板材", 1, true, "启用", "备注"
        );
        MaterialCategoryResponse b = new MaterialCategoryResponse(
                1L, "CAT001", "板材", 1, true, "启用", "备注"
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        MaterialCategoryResponse response = new MaterialCategoryResponse(
                1L, "CAT001", "板材", 1, true, "启用", "备注"
        );
        assertThat(response.toString()).contains("CAT001", "板材");
    }
}