package com.leo.erp.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存记账输入：一次来源单据审核产生的全部库存明细行。
 *
 * <p>库存模块不依赖销售/采购领域实体，由各来源工作流在此组装中性入参；
 * 仓库信息允许明细为空时回落到单据头（{@link #defaultWarehouseId} / {@link #defaultWarehouseName}）。
 *
 * @param sourceDocumentType   来源单据类型（落库字符串）
 * @param sourceDocumentId     来源单据ID
 * @param sourceDocumentNo     来源单据号快照
 * @param occurredAt           业务发生日期，取来源单据日期
 * @param defaultWarehouseId   单据头仓库ID，明细缺失时回落
 * @param defaultWarehouseName 单据头仓库名称，明细缺失时回落
 * @param lines                库存明细行
 */
public record InventoryTransactionInput(
        String sourceDocumentType,
        Long sourceDocumentId,
        String sourceDocumentNo,
        LocalDate occurredAt,
        Long defaultWarehouseId,
        String defaultWarehouseName,
        List<Line> lines
) {

    /**
     * 单条库存明细行。
     *
     * @param sourceItemId     来源单据明细ID，幂等唯一键组成部分
     * @param materialId       物料ID
     * @param materialCode     物料编码快照
     * @param warehouseId      明细仓库ID，可为空
     * @param warehouseName    明细仓库名称，可为空
     * @param batchNo          批次号快照，可为空
     * @param quantity         数量，恒为正数
     * @param quantityUnit     数量单位快照
     * @param sourceUnitPrice  来源单位价格；出库无库存量时作为成本兜底
     */
    public record Line(
            Long sourceItemId,
            Long materialId,
            String materialCode,
            Long warehouseId,
            String warehouseName,
            String batchNo,
            int quantity,
            String quantityUnit,
            BigDecimal sourceUnitPrice
    ) {
    }
}
