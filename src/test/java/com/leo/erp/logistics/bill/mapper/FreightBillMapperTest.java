package com.leo.erp.logistics.bill.mapper;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillMapperTest {

    private final FreightBillMapper mapper = Mappers.getMapper(FreightBillMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        FreightBill entity = new FreightBill();
        entity.setId(1L);
        entity.setBillNo("FB-001");
        entity.setCarrierName("物流甲");
        entity.setVehiclePlate("苏A12345");
        entity.setCustomerName("客户甲");
        entity.setProjectName("项目甲");
        entity.setBillTime(LocalDate.of(2026, 5, 4));
        entity.setUnitPrice(new BigDecimal("20.00"));
        entity.setTotalWeight(new BigDecimal("2.500"));
        entity.setTotalFreight(new BigDecimal("50.00"));
        entity.setStatus("未审核");
        entity.setDeliveryStatus("未送达");
        entity.setRemark("测试备注");

        FreightBillResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.billNo()).isEqualTo("FB-001");
        assertThat(response.carrierName()).isEqualTo("物流甲");
        assertThat(response.vehiclePlate()).isEqualTo("苏A12345");
        assertThat(response.customerName()).isEqualTo("客户甲");
        assertThat(response.projectName()).isEqualTo("项目甲");
        assertThat(response.billTime()).isEqualTo(LocalDate.of(2026, 5, 4));
        assertThat(response.unitPrice()).isEqualByComparingTo("20.00");
        assertThat(response.totalWeight()).isEqualByComparingTo("2.500");
        assertThat(response.totalFreight()).isEqualByComparingTo("50.00");
        assertThat(response.status()).isEqualTo("未审核");
        assertThat(response.deliveryStatus()).isEqualTo("未送达");
        assertThat(response.remark()).isEqualTo("测试备注");
    }

    @Test
    void shouldMapNullFieldsGracefully() {
        FreightBill entity = new FreightBill();
        entity.setId(2L);
        entity.setBillNo("FB-002");
        entity.setCarrierName("物流乙");
        entity.setCustomerName("客户乙");
        entity.setProjectName("项目乙");
        entity.setBillTime(LocalDate.of(2026, 6, 1));
        entity.setUnitPrice(new BigDecimal("10.00"));
        entity.setTotalWeight(new BigDecimal("1.000"));
        entity.setTotalFreight(new BigDecimal("10.00"));
        entity.setStatus("未审核");
        entity.setDeliveryStatus("未送达");

        FreightBillResponse response = mapper.toResponse(entity);

        assertThat(response.vehiclePlate()).isNull();
        assertThat(response.remark()).isNull();
    }
}
