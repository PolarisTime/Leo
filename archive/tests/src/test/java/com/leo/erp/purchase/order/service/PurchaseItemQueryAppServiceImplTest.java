package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseItemQueryAppServiceImplTest {

    @Test
    void shouldReturnEmptyListWhenFindingSourceInboundItemsByNullIds() {
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemQueryAppServiceImpl service = new PurchaseItemQueryAppServiceImpl(
                inboundItemQueryService, orderItemQueryService
        );

        assertThat(service.findSourceInboundItemsByIds(null)).isEmpty();
        assertThat(service.findSourceInboundItemsByIds(List.of())).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenFindingSourcePurchaseOrderItemsByNullIds() {
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemQueryAppServiceImpl service = new PurchaseItemQueryAppServiceImpl(
                inboundItemQueryService, orderItemQueryService
        );

        assertThat(service.findSourcePurchaseOrderItemsByIds(null)).isEmpty();
        assertThat(service.findSourcePurchaseOrderItemsByIds(List.of())).isEmpty();
    }

    @Test
    void shouldMapInboundItemToRecord() {
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemQueryAppServiceImpl service = new PurchaseItemQueryAppServiceImpl(
                inboundItemQueryService, orderItemQueryService
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setInboundNo("PI-001");
        inbound.setPurchaseOrderNo("PO-001");

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(1L);
        item.setPurchaseInbound(inbound);
        item.setQuantity(10);
        item.setWeighWeightTon(new BigDecimal("1.000"));
        item.setBrand("宝钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setMaterialCode("M1");
        item.setCategory("螺纹钢");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");

        when(inboundItemQueryService.findAllActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        List<PurchaseItemQueryAppService.SourceInboundItemRecord> results =
                service.findSourceInboundItemsByIds(List.of(1L));

        assertThat(results).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo(1L);
            assertThat(record.inboundNo()).isEqualTo("PI-001");
            assertThat(record.purchaseOrderNo()).isEqualTo("PO-001");
            assertThat(record.quantity()).isEqualTo(10);
            assertThat(record.weighWeightTon()).isEqualByComparingTo("1.000");
            assertThat(record.brand()).isEqualTo("宝钢");
            assertThat(record.material()).isEqualTo("HRB400");
            assertThat(record.spec()).isEqualTo("18");
            assertThat(record.materialCode()).isEqualTo("M1");
            assertThat(record.category()).isEqualTo("螺纹钢");
            assertThat(record.unit()).isEqualTo("吨");
            assertThat(record.warehouseName()).isEqualTo("一号库");
            assertThat(record.batchNo()).isEqualTo("B1");
        });
    }

    @Test
    void shouldMapPurchaseOrderItemToRecord() {
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemQueryAppServiceImpl service = new PurchaseItemQueryAppServiceImpl(
                inboundItemQueryService, orderItemQueryService
        );

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo("PO-001");

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        item.setPurchaseOrder(order);
        item.setQuantity(10);
        item.setPieceWeightTon(new BigDecimal("0.100"));
        item.setWeightTon(new BigDecimal("1.000"));
        item.setBrand("宝钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setMaterialCode("M1");
        item.setCategory("螺纹钢");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");

        when(orderItemQueryService.findActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        List<PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord> results =
                service.findSourcePurchaseOrderItemsByIds(List.of(1L));

        assertThat(results).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo(1L);
            assertThat(record.orderNo()).isEqualTo("PO-001");
            assertThat(record.quantity()).isEqualTo(10);
            assertThat(record.weightTon()).isEqualByComparingTo("1.000");
            assertThat(record.pieceWeightTon()).isEqualByComparingTo("0.100");
            assertThat(record.brand()).isEqualTo("宝钢");
            assertThat(record.material()).isEqualTo("HRB400");
            assertThat(record.spec()).isEqualTo("18");
            assertThat(record.materialCode()).isEqualTo("M1");
            assertThat(record.category()).isEqualTo("螺纹钢");
            assertThat(record.unit()).isEqualTo("吨");
            assertThat(record.warehouseName()).isEqualTo("一号库");
            assertThat(record.batchNo()).isEqualTo("B1");
        });
    }

    @Test
    void shouldHandleNullParentWhenMappingInboundItem() {
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemQueryAppServiceImpl service = new PurchaseItemQueryAppServiceImpl(
                inboundItemQueryService, orderItemQueryService
        );

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(1L);
        item.setPurchaseInbound(null);
        item.setQuantity(5);
        item.setBrand("宝钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setMaterialCode("M1");
        item.setCategory("螺纹钢");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");

        when(inboundItemQueryService.findAllActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        List<PurchaseItemQueryAppService.SourceInboundItemRecord> results =
                service.findSourceInboundItemsByIds(List.of(1L));

        assertThat(results).singleElement().satisfies(record -> {
            assertThat(record.inboundNo()).isNull();
            assertThat(record.purchaseOrderNo()).isNull();
        });
    }

    @Test
    void shouldHandleNullParentWhenMappingPurchaseOrderItem() {
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemQueryAppServiceImpl service = new PurchaseItemQueryAppServiceImpl(
                inboundItemQueryService, orderItemQueryService
        );

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        item.setPurchaseOrder(null);
        item.setQuantity(5);
        item.setPieceWeightTon(new BigDecimal("0.100"));
        item.setWeightTon(new BigDecimal("0.500"));
        item.setBrand("宝钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setMaterialCode("M1");
        item.setCategory("螺纹钢");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");

        when(orderItemQueryService.findActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        List<PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord> results =
                service.findSourcePurchaseOrderItemsByIds(List.of(1L));

        assertThat(results).singleElement().satisfies(record -> {
            assertThat(record.orderNo()).isNull();
        });
    }
}
