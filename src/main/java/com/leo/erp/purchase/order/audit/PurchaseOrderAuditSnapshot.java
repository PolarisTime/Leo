package com.leo.erp.purchase.order.audit;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@TypeName("PurchaseOrder")
public record PurchaseOrderAuditSnapshot(
        @Id Long id,
        Long version,
        String orderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        LocalDateTime orderDate,
        String buyerName,
        Long settlementCompanyId,
        String settlementCompanyName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deleted,
        String remark,
        List<PurchaseOrderItemAuditSnapshot> items
) {
    public static PurchaseOrderAuditSnapshot from(PurchaseOrder order) {
        List<PurchaseOrderItemAuditSnapshot> itemSnapshots = order.getItems().stream()
                .sorted(Comparator.comparing(PurchaseOrderItem::getLineNo)
                        .thenComparing(PurchaseOrderItem::getId))
                .map(PurchaseOrderItemAuditSnapshot::from)
                .toList();
        return new PurchaseOrderAuditSnapshot(
                order.getId(),
                order.getVersion(),
                order.getOrderNo(),
                order.getSupplierId(),
                order.getSupplierCode(),
                order.getSupplierName(),
                order.getOrderDate(),
                order.getBuyerName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                order.getTotalWeight(),
                order.getTotalAmount(),
                order.getStatus(),
                order.isDeletedFlag(),
                order.getRemark(),
                itemSnapshots
        );
    }

    @TypeName("PurchaseOrderItem")
    public record PurchaseOrderItemAuditSnapshot(
            @Id Long id,
            Integer lineNo,
            Long materialId,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            String unit,
            Long warehouseId,
            String warehouseName,
            String batchNo,
            Integer quantity,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal weightTon,
            BigDecimal unitPrice,
            BigDecimal amount,
            BigDecimal actualWeightTon
    ) {
        private static PurchaseOrderItemAuditSnapshot from(PurchaseOrderItem item) {
            return new PurchaseOrderItemAuditSnapshot(
                    item.getId(),
                    item.getLineNo(),
                    item.getMaterialId(),
                    item.getMaterialCode(),
                    item.getBrand(),
                    item.getCategory(),
                    item.getMaterial(),
                    item.getSpec(),
                    item.getLength(),
                    item.getUnit(),
                    item.getWarehouseId(),
                    item.getWarehouseName(),
                    item.getBatchNo(),
                    item.getQuantity(),
                    item.getQuantityUnit(),
                    item.getPieceWeightTon(),
                    item.getPiecesPerBundle(),
                    item.getWeightTon(),
                    item.getUnitPrice(),
                    item.getAmount(),
                    item.getActualWeightTon()
            );
        }
    }
}
