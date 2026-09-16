package com.leo.erp.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售退货来源只读查询端口。
 * <p>
 * 对账模块生成红字对账单时需要读取退货单头与明细快照，但不允许直接依赖退货模块内部实体与仓储；
 * 该端口由退货模块实现并仅暴露只读快照。
 */
public interface SalesReturnReversalSourceQuery {

    ReturnSnapshot findById(Long salesReturnId);

    record ReturnSnapshot(
            Long id,
            String returnNo,
            String status,
            Long customerId,
            String customerName,
            Long projectId,
            String projectName,
            Long settlementCompanyId,
            String settlementCompanyName,
            LocalDate returnDate,
            List<ItemSnapshot> items) {
        public ReturnSnapshot {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record ItemSnapshot(
            Long sourceSalesOrderItemId,
            Integer quantity,
            BigDecimal weightTon,
            BigDecimal amount,
            Long materialId,
            Long warehouseId,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            String unit,
            String batchNo,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal unitPrice) {
    }
}
