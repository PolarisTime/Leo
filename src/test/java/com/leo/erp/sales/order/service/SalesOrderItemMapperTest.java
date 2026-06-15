package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderItemMapperTest {

    @Test
    void applyItemFieldsShouldCopyAllFieldsFromRequest() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemMapper mapper = new SalesOrderItemMapper(materialSupport, warehouseSelectionSupport);

        SalesOrder entity = new SalesOrder();
        entity.setId(1L);

        SalesOrderItemRequest source = new SalesOrderItemRequest(
                "M1", "宝钢", "盘螺", "HRB400", "8", "12m", "吨",
                101L, "一号库", "B1", 5, "件",
                new BigDecimal("2.248"), 2, new BigDecimal("11.240"),
                new BigDecimal("3000.00"), new BigDecimal("33720.00")
        );

        SalesOrderItem item = new SalesOrderItem();
        TradeMaterialSnapshot material = material();

        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialSupport.normalizeBatchNo(material, "B1", 1, true)).thenReturn("B1");

        mapper.applyItemFields(entity, source, item, 1, material,
                new BigDecimal("11.240"), new BigDecimal("2.248"));

        assertThat(item.getSalesOrder()).isEqualTo(entity);
        assertThat(item.getLineNo()).isEqualTo(1);
        assertThat(item.getMaterialCode()).isEqualTo("M1");
        assertThat(item.getBrand()).isEqualTo("宝钢");
        assertThat(item.getCategory()).isEqualTo("盘螺");
        assertThat(item.getMaterial()).isEqualTo("HRB400");
        assertThat(item.getSpec()).isEqualTo("8");
        assertThat(item.getLength()).isEqualTo("12m");
        assertThat(item.getUnit()).isEqualTo("吨");
        assertThat(item.getSourceInboundItemId()).isEqualTo(101L);
        assertThat(item.getWarehouseName()).isEqualTo("一号库");
        assertThat(item.getBatchNo()).isEqualTo("B1");
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getQuantityUnit()).isEqualTo("件");
        assertThat(item.getPieceWeightTon()).isEqualByComparingTo("2.248");
        assertThat(item.getPiecesPerBundle()).isEqualTo(2);
        assertThat(item.getWeightTon()).isEqualByComparingTo("11.240");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("3000.00");
    }

    @Test
    void applyItemFieldsShouldNormalizeWarehouseAndBatch() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemMapper mapper = new SalesOrderItemMapper(materialSupport, warehouseSelectionSupport);

        SalesOrder entity = new SalesOrder();
        SalesOrderItemRequest source = new SalesOrderItemRequest(
                "M2", "沙钢", "螺纹钢", "HRB400", "16", null, "吨",
                null, "仓库A", null, 3, null,
                new BigDecimal("1.500"), 1, new BigDecimal("4.500"),
                new BigDecimal("4000.00"), new BigDecimal("18000.00")
        );
        SalesOrderItem item = new SalesOrderItem();
        TradeMaterialSnapshot material = material();

        when(warehouseSelectionSupport.normalizeWarehouseName("仓库A", 2, true)).thenReturn("仓库A-normalized");
        when(materialSupport.normalizeBatchNo(material, null, 2, true)).thenReturn("AUTO-BATCH");

        mapper.applyItemFields(entity, source, item, 2, material,
                new BigDecimal("4.500"), new BigDecimal("1.500"));

        assertThat(item.getWarehouseName()).isEqualTo("仓库A-normalized");
        assertThat(item.getBatchNo()).isEqualTo("AUTO-BATCH");
        verify(warehouseSelectionSupport).normalizeWarehouseName("仓库A", 2, true);
        verify(materialSupport).normalizeBatchNo(material, null, 2, true);
    }

    @Test
    void applyItemFieldsShouldHandleNullSourceInboundAndPurchaseOrderIds() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemMapper mapper = new SalesOrderItemMapper(materialSupport, warehouseSelectionSupport);

        SalesOrder entity = new SalesOrder();
        SalesOrderItemRequest source = new SalesOrderItemRequest(
                "M3", "宝钢", "板材", "Q235", "10mm", "6m", "吨",
                null, null, "一号库", null, 10, "件",
                new BigDecimal("0.500"), 1, new BigDecimal("5.000"),
                new BigDecimal("3500.00"), new BigDecimal("17500.00")
        );
        SalesOrderItem item = new SalesOrderItem();
        TradeMaterialSnapshot material = material();

        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialSupport.normalizeBatchNo(any(), any(), eq(1), eq(true))).thenReturn("B1");

        mapper.applyItemFields(entity, source, item, 1, material,
                new BigDecimal("5.000"), new BigDecimal("0.500"));

        assertThat(item.getSourceInboundItemId()).isNull();
        assertThat(item.getSourcePurchaseOrderItemId()).isNull();
    }

    @Test
    void applyItemFieldsShouldSetSourcePurchaseOrderItemId() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemMapper mapper = new SalesOrderItemMapper(materialSupport, warehouseSelectionSupport);

        SalesOrder entity = new SalesOrder();
        SalesOrderItemRequest source = new SalesOrderItemRequest(
                "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                null, 201L, "一号库", "B1", 2, "件",
                new BigDecimal("2.248"), 1, new BigDecimal("4.496"),
                new BigDecimal("3000.00"), new BigDecimal("13488.00")
        );
        SalesOrderItem item = new SalesOrderItem();
        TradeMaterialSnapshot material = material();

        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialSupport.normalizeBatchNo(material, "B1", 1, true)).thenReturn("B1");

        mapper.applyItemFields(entity, source, item, 1, material,
                new BigDecimal("4.496"), new BigDecimal("2.248"));

        assertThat(item.getSourcePurchaseOrderItemId()).isEqualTo(201L);
        assertThat(item.getSourceInboundItemId()).isNull();
    }

    private TradeMaterialSnapshot material() {
        return new TradeMaterialSnapshot("M1", Boolean.FALSE);
    }
}
