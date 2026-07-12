package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSelectionSupportTestDoubles;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    void shouldInheritMaterialAndWarehouseIdentityFromSourcePurchaseOrderItem() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        PurchaseOrder sourceOrder = new PurchaseOrder();
        sourceOrder.setId(100L);
        sourceOrder.setOrderNo("PO-001");
        PurchaseOrderItem sourceOrderItem = new PurchaseOrderItem();
        sourceOrderItem.setId(201L);
        sourceOrderItem.setPurchaseOrder(sourceOrder);
        sourceOrderItem.setMaterialId(301L);
        sourceOrderItem.setWarehouseId(401L);
        sourceOrderItem.setMaterialCode("M1");
        sourceOrderItem.setWarehouseName("一号库");

        PurchaseInboundItemRequest source = new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                201L, "一号库", "B1", 10, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("1.000"),
                new BigDecimal("4000.00"), new BigDecimal("4000.00")
        );
        PurchaseInboundItem item = new PurchaseInboundItem();
        WeightSettlementResult settlement = new WeightSettlementResult(
                new BigDecimal("1.000"), null,
                BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("0.100"), new BigDecimal("1.000")
        );

        mapper.applyItemFields(
                new PurchaseInbound(), source, item, 1, "M1",
                new TradeMaterialSnapshot(301L, "M1", true),
                Map.of(201L, sourceOrderItem),
                new InboundItemMapper.ItemMappingContext(settlement, "一号库", "理算")
        );

        assertThat(PurchaseInboundItem.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .contains("materialId", "warehouseId", "batchNoNormalized");
        assertThat(ReflectionTestUtils.getField(item, "materialId")).isEqualTo(301L);
        assertThat(ReflectionTestUtils.getField(item, "warehouseId")).isEqualTo(401L);
    }

    @Test
    void shouldMapBasicFieldsFromRequestToItem() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);

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
                inbound, source, item, 1, "M1", material(), Map.of(), ctx
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
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);

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
                inbound, source, item, 1, "M1", material(), sourceMap, ctx
        );

        assertThat(result.sourceOrderNo()).isEqualTo("PO-001");
        assertThat(result.sourcePurchaseOrderItemId()).isEqualTo(201L);
    }

    @Test
    void shouldReturnNullSourceOrderNoWhenNoSourceLinked() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);

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
                inbound, source, item, 1, "M1", material(), Map.of(), ctx
        );

        assertThat(result.sourceOrderNo()).isNull();
        assertThat(result.sourcePurchaseOrderItemId()).isNull();
    }

    @Test
    void shouldUseHeaderWarehouseWhenLineWarehouseIsBlank() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);

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

        mapper.applyItemFields(inbound, source, item, 1, "M1", material(), Map.of(), ctx);

        assertThat(item.getWarehouseName()).isEqualTo("默认仓库");
    }

    @Test
    void shouldSetWeighWeightFieldsFromSettlementResult() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);

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

        mapper.applyItemFields(inbound, source, item, 1, "M1", material(), Map.of(), ctx);

        assertThat(item.getWeighWeightTon()).isEqualByComparingTo("0.430");
        assertThat(item.getWeightAdjustmentTon()).isEqualByComparingTo("0.030");
        assertThat(item.getWeightAdjustmentAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void shouldSettlementModeFallbackToHeaderThenDefault() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);

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

        mapper.applyItemFields(inbound, source, item, 1, "M1", material(), Map.of(), ctx);

        assertThat(item.getSettlementMode()).isEqualTo("现结");
    }

    @Test
    void shouldUseLineSettlementModeWhenPresentAndHeaderWhenLineBlank() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);

        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), anyBoolean())).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        PurchaseInboundItem lineModeItem = new PurchaseInboundItem();
        mapper.applyItemFields(new PurchaseInbound(), requestWithSettlementMode("月结"), lineModeItem,
                1, "M1", material(), Map.of(), contextWithSettlementMode("现结"));

        PurchaseInboundItem headerModeItem = new PurchaseInboundItem();
        mapper.applyItemFields(new PurchaseInbound(), requestWithSettlementMode(" "), headerModeItem,
                1, "M1", material(), Map.of(), contextWithSettlementMode("承兑"));
        PurchaseInboundItem defaultModeItem = new PurchaseInboundItem();
        mapper.applyItemFields(new PurchaseInbound(), requestWithSettlementMode(" "), defaultModeItem,
                1, "M1", material(), Map.of(), contextWithSettlementMode(" "));

        assertThat(lineModeItem.getSettlementMode()).isEqualTo("月结");
        assertThat(headerModeItem.getSettlementMode()).isEqualTo("承兑");
        assertThat(defaultModeItem.getSettlementMode()).isEqualTo("现结");
    }

    @Test
    void shouldClearSettlementCompanyWhenSourceItemMissingOrDetached() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);

        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), anyBoolean())).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        PurchaseInboundItem missingSourceItem = new PurchaseInboundItem();
        InboundItemMapper.ItemMappingResult missingResult = mapper.applyItemFields(
                new PurchaseInbound(), requestWithSourceItemId(201L), missingSourceItem,
                1, "M1", material(), Map.of(), contextWithSettlementMode("现结"));

        PurchaseInboundItem detachedSourceItem = new PurchaseInboundItem();
        InboundItemMapper.ItemMappingResult detachedResult = mapper.applyItemFields(
                new PurchaseInbound(), requestWithSourceItemId(202L), detachedSourceItem,
                1, "M1", material(), Map.of(202L, new PurchaseOrderItem()), contextWithSettlementMode("现结"));

        assertThat(missingResult.sourceOrderNo()).isNull();
        assertThat(missingSourceItem.getSettlementCompanyId()).isNull();
        assertThat(missingSourceItem.getSettlementCompanyName()).isNull();
        assertThat(detachedResult.sourceOrderNo()).isNull();
        assertThat(detachedSourceItem.getSettlementCompanyId()).isNull();
        assertThat(detachedSourceItem.getSettlementCompanyName()).isNull();
    }

    @Test
    void shouldCopySettlementCompanyFromLinkedSourceOrder() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        InboundItemMapper mapper = mapper(materialSupport, warehouseSelectionSupport);

        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), anyBoolean())).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        PurchaseOrder sourceOrder = new PurchaseOrder();
        sourceOrder.setOrderNo("PO-SETTLEMENT");
        sourceOrder.setSettlementCompanyId(3001L);
        sourceOrder.setSettlementCompanyName("结算公司");
        PurchaseOrderItem sourceOrderItem = new PurchaseOrderItem();
        sourceOrderItem.setPurchaseOrder(sourceOrder);

        PurchaseInboundItem item = new PurchaseInboundItem();
        InboundItemMapper.ItemMappingResult result = mapper.applyItemFields(
                new PurchaseInbound(), requestWithSourceItemId(201L), item,
                1, "M1", material(), Map.of(201L, sourceOrderItem), contextWithSettlementMode("现结"));

        assertThat(result.sourceOrderNo()).isEqualTo("PO-SETTLEMENT");
        assertThat(item.getSettlementCompanyId()).isEqualTo(3001L);
        assertThat(item.getSettlementCompanyName()).isEqualTo("结算公司");
    }

    private PurchaseInboundItemRequest requestWithSettlementMode(String settlementMode) {
        return new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                null, "一号库", settlementMode, "B1", 10, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("1.000"),
                null, null, null,
                new BigDecimal("4000.00"), new BigDecimal("4000.00")
        );
    }

    private PurchaseInboundItemRequest requestWithSourceItemId(Long sourcePurchaseOrderItemId) {
        return new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                sourcePurchaseOrderItemId, "一号库", "现结", "B1", 10, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("1.000"),
                null, null, null,
                new BigDecimal("4000.00"), new BigDecimal("4000.00")
        );
    }

    private InboundItemMapper.ItemMappingContext contextWithSettlementMode(String settlementMode) {
        WeightSettlementResult ws = new WeightSettlementResult(
                new BigDecimal("1.000"), null,
                BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("0.100"), new BigDecimal("1.000")
        );
        return new InboundItemMapper.ItemMappingContext(ws, "一号库", settlementMode);
    }

    private TradeMaterialSnapshot material() {
        return new TradeMaterialSnapshot("M1", Boolean.FALSE);
    }

    private InboundItemMapper mapper(TradeItemMaterialSupport materialSupport,
                                     WarehouseSelectionSupport warehouseSelectionSupport) {
        WarehouseSelectionSupportTestDoubles.stubWarehouseResolution(warehouseSelectionSupport);
        return new InboundItemMapper(materialSupport, warehouseSelectionSupport);
    }
}
