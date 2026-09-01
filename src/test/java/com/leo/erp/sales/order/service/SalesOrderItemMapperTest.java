package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOrderItemMapper 快照映射回归测试：页面隐藏商品编码/批号不影响后端映射。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderItemMapperTest {

    @Mock
    private TradeItemMaterialSupport tradeItemMaterialSupport;

    @Mock
    private WarehouseSelectionSupport warehouseSelectionSupport;

    @InjectMocks
    private SalesOrderItemMapper mapper;

    private SalesOrderItemRequest request(String batchNo) {
        return new SalesOrderItemRequest(
                null, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                1L, 2L, 7L, "库房A", batchNo, 5, null, new BigDecimal("1.250"), 100,
                new BigDecimal("6.250"), new BigDecimal("4000.00"), null);
    }

    private void stubDependencies(String resolvedBatchNo) {
        when(tradeItemMaterialSupport.normalizeBatchNo(any(), anyInt())).thenReturn(resolvedBatchNo);
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), eq(true)))
                .thenReturn(new WarehouseSnapshot(7L, "W001", "库房A"));
    }

    @Test
    void applyItemFields_shouldMapAllSnapshotFields() {
        stubDependencies("B001");
        SalesOrder entity = new SalesOrder();
        SalesOrderItem item = new SalesOrderItem();
        TradeMaterialSnapshot material = new TradeMaterialSnapshot(500L, "M001");

        mapper.applyItemFields(entity, request("B001"), item, 3, "M001", material,
                new BigDecimal("6.250"), new BigDecimal("1.250"));

        assertThat(item.getSalesOrder()).isSameAs(entity);
        assertThat(item.getLineNo()).isEqualTo(3);
        assertThat(item.getMaterialId()).isEqualTo(500L);
        assertThat(item.getMaterialCode()).isEqualTo("M001");
        assertThat(item.getBrand()).isEqualTo("品牌A");
        assertThat(item.getCategory()).isEqualTo("型钢");
        assertThat(item.getMaterial()).isEqualTo("螺纹钢");
        assertThat(item.getSpec()).isEqualTo("HRB400");
        assertThat(item.getLength()).isEqualTo("12m");
        assertThat(item.getUnit()).isEqualTo("吨");
        assertThat(item.getSourceInboundItemId()).isEqualTo(1L);
        assertThat(item.getSourcePurchaseOrderItemId()).isEqualTo(2L);
        assertThat(item.getWarehouseId()).isEqualTo(7L);
        assertThat(item.getWarehouseName()).isEqualTo("库房A");
        assertThat(item.getBatchNo()).isEqualTo("B001");
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getQuantityUnit()).isEqualTo("件");
        assertThat(item.getPieceWeightTon()).isEqualByComparingTo("1.250");
        assertThat(item.getPiecesPerBundle()).isEqualTo(100);
        assertThat(item.getWeightTon()).isEqualByComparingTo("6.250");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("4000.00");
    }

    @Test
    void applyItemFields_shouldGenerateBatchNoWhenHiddenFromPage() {
        when(tradeItemMaterialSupport.normalizeBatchNo(isNull(), eq(1)))
                .thenAnswer(invocation -> String.valueOf(new SnowflakeIdGenerator(0L).nextId()));
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), eq(true)))
                .thenReturn(new WarehouseSnapshot(7L, "W001", "库房A"));
        SalesOrderItem item = new SalesOrderItem();

        mapper.applyItemFields(new SalesOrder(), request(null), item, 1, "M001",
                new TradeMaterialSnapshot(500L, "M001"), 7L,
                new BigDecimal("6.250"), new BigDecimal("1.250"));

        verify(tradeItemMaterialSupport).normalizeBatchNo(isNull(), eq(1));
        assertThat(item.getBatchNo()).isNotBlank();
        assertThat(Long.parseLong(item.getBatchNo())).isPositive();
    }

    @Test
    void applyItemFields_shouldPropagateWarehouseValidationFailure() {
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.VALIDATION_ERROR,
                "第1行仓库ID与名称不一致"))
                .when(warehouseSelectionSupport).resolveWarehouse(any(), any(), anyInt(), eq(true));
        SalesOrderItem item = new SalesOrderItem();

        assertThatThrownBy(() -> mapper.applyItemFields(new SalesOrder(), request("B001"), item, 1,
                "M001", new TradeMaterialSnapshot(500L, "M001"), 7L,
                new BigDecimal("6.250"), new BigDecimal("1.250")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仓库ID与名称不一致");
    }

    @Test
    void amount_shouldKeepTwoDecimalPrecision() {
        BigDecimal weightTon = new BigDecimal("6.250");
        BigDecimal unitPrice = new BigDecimal("4000.00");
        BigDecimal amount = com.leo.erp.common.support.TradeItemCalculator
                .calculateAmount(weightTon, unitPrice);

        assertThat(amount).isEqualByComparingTo("25000.00");
        assertThat(amount.scale()).isEqualTo(2);
    }
}
