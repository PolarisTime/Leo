package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * PurchaseOrderApplyService 明细快照映射回归测试。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderApplyServiceTest {

    @Mock
    private TradeItemMaterialSupport tradeItemMaterialSupport;

    @Mock
    private WarehouseSelectionSupport warehouseSelectionSupport;

    @Mock
    private PurchaseInboundItemQueryService purchaseInboundItemQueryService;

    @InjectMocks
    private PurchaseOrderApplyService service;

    private PurchaseOrderItemRequest itemRequest(String batchNo) {
        return new PurchaseOrderItemRequest(
                null, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                "库房A", batchNo, 5, null, new BigDecimal("1.250"), 100,
                new BigDecimal("6.250"), new BigDecimal("4000.00"), null);
    }

    private PurchaseOrderRequest request(PurchaseOrderItemRequest... items) {
        return new PurchaseOrderRequest(
                "PO001", "供应商A", LocalDateTime.of(2026, 8, 1, 10, 0),
                "采购员A", 30L, null, null, List.of(items));
    }

    private void stubHappyPath() {
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), eq(true)))
                .thenReturn(new WarehouseSnapshot(7L, "W001", "库房A"));
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
    }

    @Test
    void applyItems_shouldMapItemAndComputeTotals() {
        stubHappyPath();
        when(tradeItemMaterialSupport.normalizeRequiredBatchNo(any(), anyInt())).thenReturn("B001");
        PurchaseOrder entity = new PurchaseOrder();

        service.applyItems(entity, request(itemRequest("B001")), () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
        PurchaseOrderItem item = entity.getItems().get(0);
        assertThat(item.getLineNo()).isEqualTo(1);
        assertThat(item.getMaterialId()).isEqualTo(500L);
        assertThat(item.getMaterialCode()).isEqualTo("M001");
        assertThat(item.getBrand()).isEqualTo("品牌A");
        assertThat(item.getWarehouseId()).isEqualTo(7L);
        assertThat(item.getWarehouseName()).isEqualTo("库房A");
        assertThat(item.getBatchNo()).isEqualTo("B001");
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getWeightTon()).isEqualByComparingTo("6.25000000");
        assertThat(item.getAmount()).isEqualByComparingTo("25000.00");
        assertThat(entity.getTotalWeight()).isEqualByComparingTo("6.25000000");
        assertThat(entity.getTotalAmount()).isEqualByComparingTo("25000.00");
    }

    @Test
    void applyItems_shouldRejectBlankRequiredBatchNo() {
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), eq(true)))
                .thenReturn(new WarehouseSnapshot(7L, "W001", "库房A"));
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "第1行批号不能为空"))
                .when(tradeItemMaterialSupport).normalizeRequiredBatchNo(eq(""), eq(1));
        PurchaseOrder entity = new PurchaseOrder();

        assertThatThrownBy(() -> service.applyItems(entity, request(itemRequest("")), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批号不能为空");
    }

    @Test
    void applyItems_shouldPropagateMaterialIdCodeMismatch() {
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "第1行商品ID与编码不一致"));
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
        PurchaseOrder entity = new PurchaseOrder();

        assertThatThrownBy(() -> service.applyItems(entity, request(itemRequest("B001")), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品ID与编码不一致");
    }
}
