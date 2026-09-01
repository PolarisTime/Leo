package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * InboundItemMapper 采购入库明细快照映射回归测试。
 */
@ExtendWith(MockitoExtension.class)
class InboundItemMapperTest {

    @Mock
    private TradeItemMaterialSupport tradeItemMaterialSupport;

    @Mock
    private WarehouseSelectionSupport warehouseSelectionSupport;

    @InjectMocks
    private InboundItemMapper mapper;

    private PurchaseInboundItemRequest request(String settlementMode, String batchNo) {
        return new PurchaseInboundItemRequest(
                null, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                11L, "库房A", settlementMode, batchNo, 5, null,
                new BigDecimal("1.250"), 100, new BigDecimal("6.250"),
                new BigDecimal("6.250"), new BigDecimal("0.000"), new BigDecimal("0.00"),
                new BigDecimal("4000.00"), null);
    }

    private WeightSettlementResult weightSettlement() {
        return new WeightSettlementResult(
                new BigDecimal("6.25000000"),
                new BigDecimal("6.25000000"),
                new BigDecimal("0.00000000"),
                new BigDecimal("0.00"),
                new BigDecimal("1.25000000"),
                new BigDecimal("6.25000000")
        );
    }

    private PurchaseOrderItem sourceItem(Long materialId, String materialCode) {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo("PO001");
        order.setSettlementCompanyId(30L);
        order.setSettlementCompanyName("结算公司A");
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(11L);
        item.setMaterialId(materialId);
        item.setMaterialCode(materialCode);
        item.setWarehouseId(7L);
        item.setWarehouseName("来源仓库");
        item.setPurchaseOrder(order);
        return item;
    }

    private void stubBatchNo() {
        when(tradeItemMaterialSupport.normalizeBatchNo(any(), anyInt())).thenReturn("B001");
    }

    @Test
    void applyItemFields_shouldPreferSourceItemMaterialAndWarehouse() {
        stubBatchNo();
        PurchaseInbound inbound = new PurchaseInbound();
        PurchaseInboundItem item = new PurchaseInboundItem();
        PurchaseOrderItem sourceItem = sourceItem(500L, "M001");

        InboundItemMapper.ItemMappingResult result = mapper.applyItemFields(
                inbound, request("现结", "B001"), item, 1, "M001",
                new TradeMaterialSnapshot(500L, "M001"),
                Map.of(11L, sourceItem),
                new InboundItemMapper.ItemMappingContext(weightSettlement(), "库房A", "现结"));

        assertThat(item.getMaterialId()).isEqualTo(500L);
        assertThat(item.getMaterialCode()).isEqualTo("M001");
        assertThat(item.getSourcePurchaseOrderItemId()).isEqualTo(11L);
        assertThat(item.getWarehouseId()).isEqualTo(7L);
        assertThat(item.getWarehouseName()).isEqualTo("来源仓库");
        assertThat(item.getSettlementCompanyId()).isEqualTo(30L);
        assertThat(item.getSettlementCompanyName()).isEqualTo("结算公司A");
        assertThat(item.getBatchNo()).isEqualTo("B001");
        assertThat(item.getSettlementMode()).isEqualTo("现结");
        assertThat(item.getAmount()).isEqualByComparingTo("25000.00");
        assertThat(result.sourceOrderNo()).isEqualTo("PO001");
    }

    @Test
    void applyItemFields_shouldFallbackToMaterialAndRequestedWarehouseWhenSourceMissing() {
        stubBatchNo();
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), eq(true)))
                .thenReturn(new WarehouseSnapshot(9L, null, "选择仓库"));
        PurchaseInbound inbound = new PurchaseInbound();
        PurchaseInboundItem item = new PurchaseInboundItem();

        InboundItemMapper.ItemMappingResult result = mapper.applyItemFields(
                inbound, request("月结", "B001"), item, 1, "M001",
                new TradeMaterialSnapshot(500L, "M001"),
                Map.of(),
                new InboundItemMapper.ItemMappingContext(weightSettlement(), null, "库房A", null));

        assertThat(item.getMaterialId()).isEqualTo(500L);
        assertThat(item.getSourcePurchaseOrderItemId()).isEqualTo(11L);
        assertThat(item.getSettlementCompanyId()).isNull();
        assertThat(item.getWarehouseId()).isEqualTo(9L);
        assertThat(item.getWarehouseName()).isEqualTo("选择仓库");
        assertThat(item.getSettlementMode()).isEqualTo("月结");
        assertThat(result.sourceOrderNo()).isNull();
    }

    @Test
    void applyItemFields_shouldDefaultSettlementModeToHeaderThenCash() {
        stubBatchNo();
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), eq(true)))
                .thenReturn(new WarehouseSnapshot(9L, null, "库房A"));
        PurchaseInbound inbound = new PurchaseInbound();
        PurchaseInboundItem item = new PurchaseInboundItem();

        mapper.applyItemFields(inbound, request(null, "B001"), item, 1, "M001",
                new TradeMaterialSnapshot(500L, "M001"), Map.of(),
                new InboundItemMapper.ItemMappingContext(weightSettlement(), null, "库房A", "头结算"));

        assertThat(item.getSettlementMode()).isEqualTo("头结算");

        PurchaseInboundItem itemWithoutHeader = new PurchaseInboundItem();
        mapper.applyItemFields(inbound, request(null, "B001"), itemWithoutHeader, 1, "M001",
                new TradeMaterialSnapshot(500L, "M001"), Map.of(),
                new InboundItemMapper.ItemMappingContext(weightSettlement(), null, "库房A", null));

        assertThat(itemWithoutHeader.getSettlementMode()).isEqualTo("现结");
    }

    @Test
    void applyItemFields_shouldGenerateBatchNoWhenBlank() {
        when(tradeItemMaterialSupport.normalizeBatchNo(any(), anyInt())).thenReturn("GEN-B001");
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), eq(true)))
                .thenReturn(new WarehouseSnapshot(9L, null, "库房A"));
        PurchaseInbound inbound = new PurchaseInbound();
        PurchaseInboundItem item = new PurchaseInboundItem();

        mapper.applyItemFields(inbound, request(null, ""), item, 1, "M001",
                new TradeMaterialSnapshot(500L, "M001"), Map.of(),
                new InboundItemMapper.ItemMappingContext(weightSettlement(), null, "库房A", null));

        assertThat(item.getBatchNo()).isEqualTo("GEN-B001");
    }
}
