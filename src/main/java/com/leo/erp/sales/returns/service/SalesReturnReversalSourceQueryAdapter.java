package com.leo.erp.sales.returns.service;

import com.leo.erp.sales.api.SalesReturnReversalSourceQuery;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SalesReturnReversalSourceQueryAdapter implements SalesReturnReversalSourceQuery {

    private final SalesReturnRepository salesReturnRepository;

    public SalesReturnReversalSourceQueryAdapter(SalesReturnRepository salesReturnRepository) {
        this.salesReturnRepository = salesReturnRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnSnapshot findById(Long salesReturnId) {
        if (salesReturnId == null) {
            return null;
        }
        SalesReturn entity = salesReturnRepository.findByIdAndDeletedFlagFalse(salesReturnId).orElse(null);
        if (entity == null) {
            return null;
        }
        List<ItemSnapshot> items = (entity.getItems() == null ? List.<SalesReturnItem>of() : entity.getItems())
                .stream()
                .map(this::toItemSnapshot)
                .toList();
        return new ReturnSnapshot(
                entity.getId(),
                entity.getReturnNo(),
                entity.getStatus(),
                entity.getCustomerId(),
                entity.getCustomerName(),
                entity.getProjectId(),
                entity.getProjectName(),
                entity.getSettlementCompanyId(),
                entity.getSettlementCompanyName(),
                entity.getReturnDate(),
                items
        );
    }

    private ItemSnapshot toItemSnapshot(SalesReturnItem item) {
        return new ItemSnapshot(
                item.getSourceSalesOrderItemId(),
                item.getQuantity(),
                item.getWeightTon(),
                item.getAmount(),
                item.getMaterialId(),
                item.getWarehouseId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getBatchNo(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getUnitPrice()
        );
    }
}
