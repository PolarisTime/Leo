package com.leo.erp.master.material.mapper;

import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialMapperTest {

    private final MaterialMapper mapper = new MaterialMapperImpl();

    @Test
    void shouldMapAllFieldsToResponse() {
        Material material = new Material();
        material.setId(1L);
        material.setMaterialCode("MAT-001");
        material.setBrand("宝钢");
        material.setMaterial("Q235B");
        material.setCategory("板材");
        material.setSpec("10mm");
        material.setLength("6m");
        material.setUnit("吨");
        material.setQuantityUnit("支");
        material.setPieceWeightTon(new BigDecimal("1.234"));
        material.setPiecesPerBundle(12);
        material.setUnitPrice(new BigDecimal("500.50"));
        material.setBatchNoEnabled(true);
        material.setRemark("测试备注");

        MaterialResponse response = mapper.toResponse(material);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.materialCode()).isEqualTo("MAT-001");
        assertThat(response.brand()).isEqualTo("宝钢");
        assertThat(response.material()).isEqualTo("Q235B");
        assertThat(response.category()).isEqualTo("板材");
        assertThat(response.spec()).isEqualTo("10mm");
        assertThat(response.length()).isEqualTo("6m");
        assertThat(response.unit()).isEqualTo("吨");
        assertThat(response.quantityUnit()).isEqualTo("支");
        assertThat(response.pieceWeightTon()).isEqualByComparingTo("1.234");
        assertThat(response.piecesPerBundle()).isEqualTo(12);
        assertThat(response.unitPrice()).isEqualByComparingTo("500.50");
        assertThat(response.batchNoEnabled()).isTrue();
        assertThat(response.remark()).isEqualTo("测试备注");
    }

    @Test
    void shouldMapNullFieldsToNull() {
        Material material = new Material();
        material.setId(1L);
        material.setMaterialCode("MAT-001");

        MaterialResponse response = mapper.toResponse(material);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.materialCode()).isEqualTo("MAT-001");
        assertThat(response.brand()).isNull();
        assertThat(response.length()).isNull();
        assertThat(response.remark()).isNull();
    }
}
