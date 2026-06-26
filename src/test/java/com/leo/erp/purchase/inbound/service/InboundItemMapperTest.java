package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InboundItemMapperTest {

    @Test
    void shouldMapBasicFieldsFromRequestToItem() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = new InboundItemMapper(materialSupport, warehouseSelectionSupport);

        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.setInboundNo("PI-001");

        PurchaseInboundItemRequest source = new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                null, "一号库", "B1", 10, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("1.000"),
                new BigDecimal("4000.00"), new BigDecimal("4000.00")
        );

        PurchaseInboundItem item = new PurchaseInboundItem();
        WeightSettlementResult ws = new WeightSettlementResult(
                new BigDecimal("1.000"), null,
                BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("0.100"), new BigDecimal("1.000")
        );
        InboundItemMapper.ItemMappingContext ctx = new InboundItemMapper.ItemMappingContext(
                ws, "一号库", "理算"
        );

        InboundItemMapper.ItemMappingResult result = mapper.applyItemFields(
                inbound, source, item, 1, material(), Map.of(), ctx
        );

        assertThat(item.getLineNo()).isEqualTo(1);
        assertThat(item.getMaterialCode()).isEqualTo("M1");
        assertThat(item.getBrand()).isEqualTo("宝钢");
        assertThat(item.getCategory()).isEqualTo("螺纹钢");
        assertThat(item.getMaterial()).isEqualTo("HRB400");
        assertThat(item.getSpec()).isEqualTo("18");
        assertThat(item.getLength()).isEqualTo("12m");
        assertThat(item.getUnit()).isEqualTo("吨");
        assertThat(item.getWarehouseName()).isEqualTo("一号库");
        assertThat(item.getBatchNo()).isEqualTo("B1");
        assertThat(item.getQuantity()).isEqualTo(10);
        assertThat(item.getQuantityUnit()).isEqualTo("支");
        assertThat(item.getPiecesPerBundle()).isEqualTo(1);
        assertThat(item.getPieceWeightTon()).isEqualByComparingTo("0.100");
        assertThat(item.getWeightTon()).isEqualByComparingTo("1.000");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("4000.00");
        assertThat(item.getPurchaseInbound()).isEqualTo(inbound);
    }

    @Test
    void shouldResolveSourceOrderNoWhenSourceItemLinked() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = new InboundItemMapper(materialSupport, warehouseSelectionSupport);

        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), anyBoolean())).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName(anyString(), anyInt(), eq(true))).thenReturn("一号库");

        PurchaseOrder sourceOrder = new PurchaseOrder();
        sourceOrder.setId(100L);
        sourceOrder.setOrderNo("PO-001");
        PurchaseOrderItem sourceOrderItem = new PurchaseOrderItem();
        sourceOrderItem.setId(201L);
        sourceOrderItem.setPurchaseOrder(sourceOrder);

        Map<Long, PurchaseOrderItem> sourceMap = Map.of(201L, sourceOrderItem);

        PurchaseInbound inbound = new PurchaseInbound();
        PurchaseInboundItemRequest source = new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                201L, "一号库", "B1", 10, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("1.000"),
                new BigDecimal("4000.00"), new BigDecimal("4000.00")
        );
        PurchaseInboundItem item = new PurchaseInboundItem();
        WeightSettlementResult ws = new WeightSettlementResult(
                new BigDecimal("1.000"), null,
                BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("0.100"), new BigDecimal("1.000")
        );
        InboundItemMapper.ItemMappingContext ctx = new InboundItemMapper.ItemMappingContext(ws, "一号库", "理算");

        InboundItemMapper.ItemMappingResult result = mapper.applyItemFields(
                inbound, source, item, 1, material(), sourceMap, ctx
        );

        assertThat(result.sourceOrderNo()).isEqualTo("PO-001");
        assertThat(result.sourcePurchaseOrderItemId()).isEqualTo(201L);
    }

    @Test
    void shouldReturnNullSourceOrderNoWhenNoSourceLinked() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = new InboundItemMapper(materialSupport, warehouseSelectionSupport);

        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), anyBoolean())).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName(anyString(), anyInt(), eq(true))).thenReturn("一号库");

        PurchaseInbound inbound = new PurchaseInbound();
        PurchaseInboundItemRequest source = new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                null, "一号库", "B1", 10, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("1.000"),
                new BigDecimal("4000.00"), new BigDecimal("4000.00")
        );
        PurchaseInboundItem item = new PurchaseInboundItem();
        WeightSettlementResult ws = new WeightSettlementResult(
                new BigDecimal("1.000"), null,
                BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("0.100"), new BigDecimal("1.000")
        );
        InboundItemMapper.ItemMappingContext ctx = new InboundItemMapper.ItemMappingContext(ws, "一号库", "理算");

        InboundItemMapper.ItemMappingResult result = mapper.applyItemFields(
                inbound, source, item, 1, material(), Map.of(), ctx
        );

        assertThat(result.sourceOrderNo()).isNull();
        assertThat(result.sourcePurchaseOrderItemId()).isNull();
    }

    @Test
    void shouldUseHeaderWarehouseWhenLineWarehouseIsBlank() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = new InboundItemMapper(materialSupport, warehouseSelectionSupport);

        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), anyBoolean())).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("默认仓库", 1, true)).thenReturn("默认仓库");

        PurchaseInbound inbound = new PurchaseInbound();
        PurchaseInboundItemRequest source = new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                null, null, "B1", 10, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("1.000"),
                new BigDecimal("4000.00"), new BigDecimal("4000.00")
        );
        PurchaseInboundItem item = new PurchaseInboundItem();
        WeightSettlementResult ws = new WeightSettlementResult(
                new BigDecimal("1.000"), null,
                BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("0.100"), new BigDecimal("1.000")
        );
        InboundItemMapper.ItemMappingContext ctx = new InboundItemMapper.ItemMappingContext(ws, "默认仓库", "理算");

        mapper.applyItemFields(inbound, source, item, 1, material(), Map.of(), ctx);

        assertThat(item.getWarehouseName()).isEqualTo("默认仓库");
    }

    @Test
    void shouldSetWeighWeightFieldsFromSettlementResult() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = new InboundItemMapper(materialSupport, warehouseSelectionSupport);

        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), anyBoolean())).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        PurchaseInbound inbound = new PurchaseInbound();
        PurchaseInboundItemRequest source = new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "盘螺", "HRB400", "18", "12m", "吨",
                null, "一号库", "过磅", "B1", 4, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                new BigDecimal("0.430"), new BigDecimal("0.030"), new BigDecimal("120.00"),
                new BigDecimal("4000.00"), new BigDecimal("1600.00")
        );
        PurchaseInboundItem item = new PurchaseInboundItem();
        WeightSettlementResult ws = new WeightSettlementResult(
                new BigDecimal("0.400"), new BigDecimal("0.430"),
                new BigDecimal("0.030"), new BigDecimal("120.00"),
                new BigDecimal("0.108"), new BigDecimal("0.430")
        );
        InboundItemMapper.ItemMappingContext ctx = new InboundItemMapper.ItemMappingContext(ws, "一号库", "过磅");

        mapper.applyItemFields(inbound, source, item, 1, material(), Map.of(), ctx);

        assertThat(item.getWeighWeightTon()).isEqualByComparingTo("0.430");
        assertThat(item.getWeightAdjustmentTon()).isEqualByComparingTo("0.030");
        assertThat(item.getWeightAdjustmentAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void shouldSettlementModeFallbackToHeaderThenDefault() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = new InboundItemMapper(materialSupport, warehouseSelectionSupport);

        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), anyBoolean())).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        PurchaseInbound inbound = new PurchaseInbound();
        PurchaseInboundItemRequest source = new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                null, "一号库", "B1", 10, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("1.000"),
                new BigDecimal("4000.00"), new BigDecimal("4000.00")
        );
        PurchaseInboundItem item = new PurchaseInboundItem();
        WeightSettlementResult ws = new WeightSettlementResult(
                new BigDecimal("1.000"), null,
                BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("0.100"), new BigDecimal("1.000")
        );
        InboundItemMapper.ItemMappingContext ctx = new InboundItemMapper.ItemMappingContext(ws, "一号库", null);

        mapper.applyItemFields(inbound, source, item, 1, material(), Map.of(), ctx);

        assertThat(item.getSettlementMode()).isEqualTo("现结");
    }

    private TradeMaterialSnapshot material() {
        return new TradeMaterialSnapshot("M1", Boolean.FALSE);
    }
}
