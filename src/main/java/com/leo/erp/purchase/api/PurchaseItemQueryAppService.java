package com.leo.erp.purchase.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 采购来源明细公开查询接口。
 * 实现由 purchase 模块提供，调用方无需接触采购实体或 Repository。
 */
public interface PurchaseItemQueryAppService {

    /** 查询采购入库明细源数据（用于销售订单默认值填充和货源追溯） */
    List<SourceInboundItemRecord> findSourceInboundItemsByIds(Collection<Long> ids);

    /** 查询采购订单明细源数据 */
    List<SourcePurchaseOrderItemRecord> findSourcePurchaseOrderItemsByIds(Collection<Long> ids);

    /** 查询历史采购订单明细快照，包含已软删除父单，仅供既有来源关系追溯 */
    List<SourcePurchaseOrderItemRecord> findPurchaseOrderItemSnapshotsByIds(Collection<Long> ids);

    record SourceInboundItemRecord(
            Long id,
            String inboundNo,
            String inboundStatus,
            String purchaseOrderNo,
            String purchaseOrderStatus,
            Integer quantity,
            BigDecimal weighWeightTon,
            String brand,
            String material,
            String spec,
            String materialCode,
            String category,
            String unit,
            String warehouseName,
            String batchNo,
            Long settlementCompanyId,
            String settlementCompanyName,
            Long materialId,
            Long warehouseId,
            String batchNoNormalized,
            String length,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle
    ) {
        public SourceInboundItemRecord(
                Long id,
                String inboundNo,
                String inboundStatus,
                String purchaseOrderNo,
                String purchaseOrderStatus,
                Integer quantity,
                BigDecimal weighWeightTon,
                String brand,
                String material,
                String spec,
                String materialCode,
                String category,
                String unit,
                String warehouseName,
                String batchNo,
                Long settlementCompanyId,
                String settlementCompanyName,
                Long materialId,
                Long warehouseId,
                String batchNoNormalized
        ) {
            this(id, inboundNo, inboundStatus, purchaseOrderNo, purchaseOrderStatus, quantity, weighWeightTon,
                    brand, material, spec, materialCode, category, unit, warehouseName, batchNo,
                    settlementCompanyId, settlementCompanyName, materialId, warehouseId, batchNoNormalized,
                    null, null, null, null);
        }

        public SourceInboundItemRecord(
                Long id,
                String inboundNo,
                String inboundStatus,
                String purchaseOrderNo,
                String purchaseOrderStatus,
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
        ) {
            this(id, inboundNo, inboundStatus, purchaseOrderNo, purchaseOrderStatus, quantity, weighWeightTon,
                    brand, material, spec, materialCode, category, unit, warehouseName, batchNo,
                    null, null, null, null, null);
        }

        public SourceInboundItemRecord(
                Long id,
                String inboundNo,
                String inboundStatus,
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
                String batchNo,
                Long settlementCompanyId,
                String settlementCompanyName
        ) {
            this(id, inboundNo, inboundStatus, purchaseOrderNo, null, quantity, weighWeightTon, brand, material,
                    spec, materialCode, category, unit, warehouseName, batchNo, settlementCompanyId,
                    settlementCompanyName, null, null, null);
        }

        public SourceInboundItemRecord(
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
        ) {
            this(id, inboundNo, null, purchaseOrderNo, null, quantity, weighWeightTon, brand, material, spec,
                    materialCode, category, unit, warehouseName, batchNo, null, null, null, null, null);
        }

        public SourceInboundItemRecord(
                Long id,
                String inboundNo,
                String inboundStatus,
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
        ) {
            this(id, inboundNo, inboundStatus, purchaseOrderNo, null, quantity, weighWeightTon, brand, material, spec,
                    materialCode, category, unit, warehouseName, batchNo, null, null, null, null, null);
        }
    }

    record SourcePurchaseOrderItemRecord(
            Long id,
            Integer quantity,
            BigDecimal weightTon,
            BigDecimal pieceWeightTon,
            String orderNo,
            String orderStatus,
            String brand,
            String material,
            String spec,
            String materialCode,
            String category,
            String unit,
            String warehouseName,
            String batchNo,
            Long settlementCompanyId,
            String settlementCompanyName,
            Long materialId,
            Long warehouseId,
            String batchNoNormalized,
            String length,
            String quantityUnit,
            Integer piecesPerBundle
    ) {
        public SourcePurchaseOrderItemRecord(
                Long id,
                Integer quantity,
                BigDecimal weightTon,
                BigDecimal pieceWeightTon,
                String orderNo,
                String orderStatus,
                String brand,
                String material,
                String spec,
                String materialCode,
                String category,
                String unit,
                String warehouseName,
                String batchNo,
                Long settlementCompanyId,
                String settlementCompanyName,
                Long materialId,
                Long warehouseId,
                String batchNoNormalized
        ) {
            this(id, quantity, weightTon, pieceWeightTon, orderNo, orderStatus, brand, material, spec,
                    materialCode, category, unit, warehouseName, batchNo, settlementCompanyId,
                    settlementCompanyName, materialId, warehouseId, batchNoNormalized, null, null, null);
        }

        public SourcePurchaseOrderItemRecord(
                Long id,
                Integer quantity,
                BigDecimal weightTon,
                BigDecimal pieceWeightTon,
                String orderNo,
                String orderStatus,
                String brand,
                String material,
                String spec,
                String materialCode,
                String category,
                String unit,
                String warehouseName,
                String batchNo,
                Long settlementCompanyId,
                String settlementCompanyName
        ) {
            this(id, quantity, weightTon, pieceWeightTon, orderNo, orderStatus, brand, material, spec,
                    materialCode, category, unit, warehouseName, batchNo, settlementCompanyId,
                    settlementCompanyName, null, null, null);
        }

        public SourcePurchaseOrderItemRecord(
                Long id,
                Integer quantity,
                BigDecimal weightTon,
                String orderNo,
                String orderStatus,
                String brand,
                String material,
                String spec,
                String materialCode,
                String category,
                String unit,
                String warehouseName,
                String batchNo
        ) {
            this(id, quantity, weightTon, null, orderNo, orderStatus, brand, material, spec, materialCode,
                    category, unit, warehouseName, batchNo, null, null, null, null, null);
        }

        public SourcePurchaseOrderItemRecord(
                Long id,
                Integer quantity,
                BigDecimal weightTon,
                BigDecimal pieceWeightTon,
                String orderNo,
                String orderStatus,
                String brand,
                String material,
                String spec,
                String materialCode,
                String category,
                String unit,
                String warehouseName,
                String batchNo
        ) {
            this(id, quantity, weightTon, pieceWeightTon, orderNo, orderStatus, brand, material, spec, materialCode,
                    category, unit, warehouseName, batchNo, null, null, null, null, null);
        }

        public SourcePurchaseOrderItemRecord(
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
        ) {
            this(id, quantity, weightTon, null, orderNo, null, brand, material, spec, materialCode, category, unit,
                    warehouseName, batchNo, null, null, null, null, null);
        }
    }

}
