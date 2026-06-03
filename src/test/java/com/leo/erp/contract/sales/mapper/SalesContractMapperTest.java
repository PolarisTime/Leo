package com.leo.erp.contract.sales.mapper;

import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SalesContractMapperTest {

    private final SalesContractMapper mapper = Mappers.getMapper(SalesContractMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        var entity = new SalesContract();
        entity.setId(1L);
        entity.setContractNo("SC-001");
        entity.setCustomerName("客户A");
        entity.setProjectName("项目A");
        entity.setSignDate(LocalDate.of(2024, 1, 15));
        entity.setEffectiveDate(LocalDate.of(2024, 1, 15));
        entity.setExpireDate(LocalDate.of(2025, 1, 15));
        entity.setSalesName("销售甲");
        entity.setTotalWeight(new BigDecimal("100.000"));
        entity.setTotalAmount(new BigDecimal("300000.00"));
        entity.setStatus("草稿");
        entity.setRemark("备注信息");

        SalesContractResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.contractNo()).isEqualTo("SC-001");
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.signDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(response.effectiveDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(response.expireDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(response.salesName()).isEqualTo("销售甲");
        assertThat(response.totalWeight()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
        assertThat(response.status()).isEqualTo("草稿");
        assertThat(response.remark()).isEqualTo("备注信息");
    }

    @Test
    void shouldMapNullEntityToNull() {
        SalesContractResponse response = mapper.toResponse(null);
        assertThat(response).isNull();
    }

    @Test
    void shouldMapEntityWithNullFields() {
        var entity = new SalesContract();
        entity.setId(1L);
        entity.setContractNo("SC-001");
        entity.setCustomerName("客户A");
        entity.setProjectName("项目A");
        entity.setSignDate(LocalDate.now());
        entity.setEffectiveDate(LocalDate.now());
        entity.setExpireDate(LocalDate.now().plusYears(1));
        entity.setSalesName("销售甲");

        SalesContractResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.totalWeight()).isNull();
        assertThat(response.totalAmount()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.remark()).isNull();
    }

    @Test
    void shouldIgnoreItemsInMapping() {
        var entity = new SalesContract();
        entity.setId(1L);
        entity.setContractNo("SC-001");
        entity.setCustomerName("客户A");
        entity.setProjectName("项目A");
        entity.setSignDate(LocalDate.now());
        entity.setEffectiveDate(LocalDate.now());
        entity.setExpireDate(LocalDate.now().plusYears(1));
        entity.setSalesName("销售甲");
        entity.setTotalWeight(BigDecimal.TEN);
        entity.setTotalAmount(new BigDecimal("100"));
        entity.setStatus("草稿");

        SalesContractResponse response = mapper.toResponse(entity);

        assertThat(response.items()).isNull();
    }
}
