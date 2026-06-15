package com.leo.erp.statement.supplier.service;

import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatementItem;
import com.leo.erp.statement.supplier.mapper.SupplierStatementMapper;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import org.springframework.stereotype.Service;

@Service
public class SupplierStatementResponseAssembler {

    private final SupplierStatementMapper supplierStatementMapper;

    public SupplierStatementResponseAssembler(SupplierStatementMapper supplierStatementMapper) {
        this.supplierStatementMapper = supplierStatementMapper;
    }

    SupplierStatementResponse toSummaryResponse(SupplierStatement entity) {
        return supplierStatementMapper.toResponse(entity);
    }

    SupplierStatementResponse toDetailResponse(SupplierStatement entity) {
        SupplierStatementResponse response = supplierStatementMapper.toResponse(entity);
        return new SupplierStatementResponse(
                response.id(),
                response.statementNo(),
                response.supplierCode(),
                response.supplierName(),
                response.startDate(),
                response.endDate(),
                response.purchaseAmount(),
                response.paymentAmount(),
                response.closingAmount(),
                response.status(),
                response.remark(),
                entity.getItems().stream().map(this::toItemResponse).toList()
        );
    }

    private SupplierStatementItemResponse toItemResponse(SupplierStatementItem item) {
        return new SupplierStatementItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getSourceInboundItemId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getWeighWeightTon(),
                item.getWeightAdjustmentTon(),
                item.getWeightAdjustmentAmount(),
                item.getUnitPrice(),
                item.getAmount()
        );
    }
}
