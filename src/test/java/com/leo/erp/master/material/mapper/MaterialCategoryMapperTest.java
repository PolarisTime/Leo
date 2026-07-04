package com.leo.erp.master.material.mapper;

import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialCategoryMapperTest {

    private final MaterialCategoryMapper mapper = new MaterialCategoryMapperImpl();

    @Test
    void shouldMapAllFieldsToResponse() {
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

        MaterialCategoryResponse response = mapper.toResponse(category);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.categoryCode()).isEqualTo("CAT001");
        assertThat(response.categoryName()).isEqualTo("钢材");
        assertThat(response.sortOrder()).isEqualTo(10);
        assertThat(response.purchaseWeighRequired()).isTrue();
        assertThat(response.purchaseWeighOverTolerancePercent()).isEqualByComparingTo("3.00");
        assertThat(response.purchaseWeighUnderTolerancePercent()).isEqualByComparingTo("4.00");
        assertThat(response.status()).isEqualTo("正常");
        assertThat(response.remark()).isEqualTo("测试备注");
    }

    @Test
    void shouldMapToOptionResponseWithValueAndLabelFromCategoryName() {
        MaterialCategory category = new MaterialCategory();
        category.setId(1L);
        category.setCategoryCode("CAT001");
        category.setCategoryName("钢材");
        category.setPurchaseWeighRequired(false);
        category.setPurchaseWeighOverTolerancePercent(new BigDecimal("3.00"));
        category.setPurchaseWeighUnderTolerancePercent(new BigDecimal("4.00"));

        MaterialCategoryOptionResponse response = mapper.toOptionResponse(category);

        assertThat(response.value()).isEqualTo("钢材");
        assertThat(response.label()).isEqualTo("钢材");
        assertThat(response.purchaseWeighRequired()).isFalse();
        assertThat(response.purchaseWeighOverTolerancePercent()).isEqualByComparingTo("3.00");
        assertThat(response.purchaseWeighUnderTolerancePercent()).isEqualByComparingTo("4.00");
    }

    @Test
    void shouldReturnNull_whenCategoryIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toOptionResponse(null)).isNull();
    }
}
