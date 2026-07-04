package com.leo.erp.sales.outbound.mapper;

import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOutboundMapperTest {

    private final SalesOutboundMapper mapper = Mappers.getMapper(SalesOutboundMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        SalesOutbound entity = new SalesOutbound();
        entity.setId(1L);
        entity.setOutboundNo("SOO-001");
        entity.setSalesOrderNo("SO-001");
        entity.setCustomerName("客户甲");
        entity.setProjectName("项目A");
        entity.setWarehouseName("一号库");
        entity.setOutboundDate(LocalDate.of(2026, 5, 20));
        entity.setTotalWeight(new BigDecimal("10.000"));
        entity.setTotalAmount(new BigDecimal("30000.00"));
        entity.setStatus("已审核");
        entity.setRemark("测试备注");

        SalesOutboundResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.outboundNo()).isEqualTo("SOO-001");
        assertThat(response.salesOrderNo()).isEqualTo("SO-001");
        assertThat(response.customerName()).isEqualTo("客户甲");
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.warehouseName()).isEqualTo("一号库");
        assertThat(response.outboundDate()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(response.totalWeight()).isEqualByComparingTo("10.000");
        assertThat(response.totalAmount()).isEqualByComparingTo("30000.00");
        assertThat(response.status()).isEqualTo("已审核");
        assertThat(response.remark()).isEqualTo("测试备注");
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        SalesOutbound entity = new SalesOutbound();
        entity.setId(2L);
        entity.setOutboundNo("SOO-002");
        entity.setSalesOrderNo(null);
        entity.setCustomerName("客户乙");
        entity.setProjectName("项目B");
        entity.setWarehouseName("二号库");
        entity.setOutboundDate(LocalDate.of(2026, 6, 15));
        entity.setTotalWeight(new BigDecimal("5.000"));
        entity.setTotalAmount(new BigDecimal("15000.00"));
        entity.setStatus("草稿");
        entity.setRemark(null);

        SalesOutboundResponse response = mapper.toResponse(entity);

        assertThat(response.salesOrderNo()).isNull();
        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
