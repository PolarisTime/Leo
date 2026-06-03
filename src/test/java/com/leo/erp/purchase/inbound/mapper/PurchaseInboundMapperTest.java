package com.leo.erp.purchase.inbound.mapper;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseInboundMapperTest {

    private final PurchaseInboundMapper mapper = Mappers.getMapper(PurchaseInboundMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        PurchaseInbound entity = new PurchaseInbound();
        entity.setId(1L);
        entity.setInboundNo("PI-001");
        entity.setPurchaseOrderNo("PO-001");
        entity.setSupplierName("供应商甲");
        entity.setWarehouseName("一号库");
        entity.setInboundDate(LocalDate.of(2026, 5, 15));
        entity.setSettlementMode("理算");
        entity.setTotalWeight(new BigDecimal("10.000"));
        entity.setTotalAmount(new BigDecimal("40000.00"));
        entity.setStatus("已入库");
        entity.setRemark("测试备注");

        PurchaseInboundResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.inboundNo()).isEqualTo("PI-001");
        assertThat(response.purchaseOrderNo()).isEqualTo("PO-001");
        assertThat(response.supplierName()).isEqualTo("供应商甲");
        assertThat(response.warehouseName()).isEqualTo("一号库");
        assertThat(response.inboundDate()).isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(response.settlementMode()).isEqualTo("理算");
        assertThat(response.totalWeight()).isEqualByComparingTo("10.000");
        assertThat(response.totalAmount()).isEqualByComparingTo("40000.00");
        assertThat(response.status()).isEqualTo("已入库");
        assertThat(response.remark()).isEqualTo("测试备注");
        assertThat(response.items()).isNull();
        assertThat(response.totalWeighWeightTon()).isNull();
        assertThat(response.totalWeightAdjustmentTon()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        PurchaseInbound entity = new PurchaseInbound();
        entity.setId(2L);
        entity.setInboundNo("PI-002");
        entity.setPurchaseOrderNo(null);
        entity.setSupplierName("供应商乙");
        entity.setWarehouseName("二号库");
        entity.setInboundDate(LocalDate.of(2026, 6, 1));
        entity.setSettlementMode("过磅");
        entity.setTotalWeight(new BigDecimal("5.000"));
        entity.setTotalAmount(new BigDecimal("20000.00"));
        entity.setStatus("待审核");
        entity.setRemark(null);

        PurchaseInboundResponse response = mapper.toResponse(entity);

        assertThat(response.purchaseOrderNo()).isNull();
        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
        assertThat(response.totalWeighWeightTon()).isNull();
        assertThat(response.totalWeightAdjustmentTon()).isNull();
    }
}
