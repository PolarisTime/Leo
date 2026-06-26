package com.leo.erp.master.material.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialCategoryTest {

    @Test
    void shouldSetAndGetAllFields() {
        MaterialCategory category = new MaterialCategory();
        category.setId(1L);
        category.setCategoryCode("CAT001");
        category.setCategoryName("钢材");
        category.setSortOrder(10);
        category.setPurchaseWeighRequired(true);
        category.setPurchaseWeighOverTolerancePercent(new BigDecimal("3.00"));
        category.setPurchaseWeighUnderTolerancePercent(new BigDecimal("4.00"));
        category.setStatus("正常");
        category.setRemark("测试备注");

        assertThat(category.getId()).isEqualTo(1L);
        assertThat(category.getCategoryCode()).isEqualTo("CAT001");
        assertThat(category.getCategoryName()).isEqualTo("钢材");
        assertThat(category.getSortOrder()).isEqualTo(10);
        assertThat(category.getPurchaseWeighRequired()).isTrue();
        assertThat(category.getPurchaseWeighOverTolerancePercent()).isEqualByComparingTo("3.00");
        assertThat(category.getPurchaseWeighUnderTolerancePercent()).isEqualByComparingTo("4.00");
        assertThat(category.getStatus()).isEqualTo("正常");
        assertThat(category.getRemark()).isEqualTo("测试备注");
    }

    @Test
    void shouldHaveDefaultPurchaseWeighRequiredAsFalse() {
        MaterialCategory category = new MaterialCategory();
        assertThat(category.getPurchaseWeighRequired()).isFalse();
        assertThat(category.getPurchaseWeighOverTolerancePercent()).isEqualByComparingTo("5.00");
        assertThat(category.getPurchaseWeighUnderTolerancePercent()).isEqualByComparingTo("5.00");
    }
}
