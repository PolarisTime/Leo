package com.leo.erp.contract.purchase.mapper;

import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseContractMapperTest {

    private final PurchaseContractMapper mapper = Mappers.getMapper(PurchaseContractMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        var entity = new PurchaseContract();
        entity.setId(1L);
        entity.setContractNo("PC-001");
        entity.setSupplierName("供应商A");
        entity.setSignDate(LocalDate.of(2024, 1, 15));
        entity.setEffectiveDate(LocalDate.of(2024, 1, 15));
        entity.setExpireDate(LocalDate.of(2025, 1, 15));
        entity.setBuyerName("采购甲");
        entity.setTotalWeight(new BigDecimal("100.000"));
        entity.setTotalAmount(new BigDecimal("300000.00"));
        entity.setStatus("草稿");
        entity.setRemark("备注信息");

        PurchaseContractResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.contractNo()).isEqualTo("PC-001");
        assertThat(response.supplierName()).isEqualTo("供应商A");
        assertThat(response.signDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(response.effectiveDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(response.expireDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(response.buyerName()).isEqualTo("采购甲");
        assertThat(response.totalWeight()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
        assertThat(response.status()).isEqualTo("草稿");
        assertThat(response.remark()).isEqualTo("备注信息");
    }

    @Test
    void shouldMapNullEntityToNull() {
        PurchaseContractResponse response = mapper.toResponse(null);
        assertThat(response).isNull();
    }

    @Test
    void shouldMapEntityWithNullFields() {
        var entity = new PurchaseContract();
        entity.setId(1L);
        entity.setContractNo("PC-001");
        entity.setSupplierName("供应商A");
        entity.setSignDate(LocalDate.now());
        entity.setEffectiveDate(LocalDate.now());
        entity.setExpireDate(LocalDate.now().plusYears(1));
        entity.setBuyerName("采购甲");

        PurchaseContractResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.totalWeight()).isNull();
        assertThat(response.totalAmount()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.remark()).isNull();
    }

    @Test
    void shouldIgnoreItemsInMapping() {
        var entity = new PurchaseContract();
        entity.setId(1L);
        entity.setContractNo("PC-001");
        entity.setSupplierName("供应商A");
        entity.setSignDate(LocalDate.now());
        entity.setEffectiveDate(LocalDate.now());
        entity.setExpireDate(LocalDate.now().plusYears(1));
        entity.setBuyerName("采购甲");
        entity.setTotalWeight(BigDecimal.TEN);
        entity.setTotalAmount(new BigDecimal("100"));
        entity.setStatus("草稿");

        PurchaseContractResponse response = mapper.toResponse(entity);

        assertThat(response.items()).isNull();
    }
}
