package com.leo.erp.sales.order.service;

import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.sales.api.SalesOrderSourceItemSnapshot;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class SalesOrderLogisticsSourceQueryService implements SalesOrderLogisticsSourceQuery {

    private final SalesOrderRepository salesOrderRepository;

    public SalesOrderLogisticsSourceQueryService(SalesOrderRepository salesOrderRepository) {
        this.salesOrderRepository = salesOrderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesOrderSourceSnapshot> findByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return salesOrderRepository.findByIdInAndDeletedFlagFalse(orderIds).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesOrderSourceSnapshot> findBySourceItemIds(Collection<Long> sourceItemIds) {
        if (sourceItemIds == null || sourceItemIds.isEmpty()) {
            return List.of();
        }
        return salesOrderRepository.findAllWithItemsBySourceItemIds(sourceItemIds).stream()
                .map(this::toSnapshot)
                .toList();
    }

    private SalesOrderSourceSnapshot toSnapshot(SalesOrder order) {
        return new SalesOrderSourceSnapshot(
                order.getId(),
                order.getOrderNo(),
                order.getPurchaseInboundNo(),
                order.getPurchaseOrderNo(),
                order.getCustomerCode(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getProjectId(),
                order.getProjectName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                order.getDeliveryDate(),
                order.getSalesName(),
                order.getTotalWeight(),
                order.getTotalAmount(),
                order.getStatus(),
                order.isDeletedFlag(),
                order.getRemark(),
                order.getItems().stream().map(this::toItemSnapshot).toList()
        );
    }

    private SalesOrderSourceItemSnapshot toItemSnapshot(SalesOrderItem item) {
        return new SalesOrderSourceItemSnapshot(
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
                item.getSourceInboundItemId(),
                item.getSourcePurchaseOrderItemId(),
                item.getSettlementCompanyId(),
                item.getSettlementCompanyName(),
                item.getWarehouseId(),
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getBatchNoNormalized(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount(),
                item.getOriginalWeightTon()
        );
    }
}
