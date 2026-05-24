package com.leo.erp.allocation.appservice;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Application Service：销售模块查询采购明细的抽象接口。
 * 实现类由 purchase 模块提供，打破 sales→purchase 直接依赖。
 */
public interface PurchaseItemQueryAppService {

    /** 查询采购入库明细源数据（用于销售订单默认值填充和货源追溯） */
    List<SourceInboundItemRecord> findSourceInboundItemsByIds(Collection<Long> ids);

    /** 查询采购订单明细源数据 */
    List<SourcePurchaseOrderItemRecord> findSourcePurchaseOrderItemsByIds(Collection<Long> ids);

    record SourceInboundItemRecord(
            Long id,
            String inboundNo,
            String purchaseOrderNo,
            Integer quantity,
            BigDecimal weighWeightTon,
            String brand,
            String material,
            String spec,
            String materialCode,
            String category,
            String unit,
            String warehouseName,
            String batchNo
    ) {}

    record SourcePurchaseOrderItemRecord(
            Long id,
            Integer quantity,
            BigDecimal weightTon,
            String orderNo,
            String brand,
            String material,
            String spec,
            String materialCode,
            String category,
            String unit,
            String warehouseName,
            String batchNo
    ) {}

    record PieceWeightSummary(Long purchaseOrderItemId, BigDecimal remainingWeight) {}
}
