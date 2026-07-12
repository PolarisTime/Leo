package com.leo.erp.statement.customer.service;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapper;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import org.springframework.stereotype.Service;

@Service
public class CustomerStatementResponseAssembler {

    private final CustomerStatementMapper customerStatementMapper;

    public CustomerStatementResponseAssembler(CustomerStatementMapper customerStatementMapper) {
        this.customerStatementMapper = customerStatementMapper;
    }

    CustomerStatementResponse toSummaryResponse(CustomerStatement entity) {
        return customerStatementMapper.toResponse(entity);
    }

    CustomerStatementResponse toDetailResponse(CustomerStatement entity) {
        CustomerStatementResponse response = customerStatementMapper.toResponse(entity);
        return new CustomerStatementResponse(
                response.id(),
                response.statementNo(),
                response.customerCode(),
                response.customerName(),
                response.projectId(),
                response.projectName(),
                response.settlementCompanyId(),
                response.settlementCompanyName(),
                response.startDate(),
                response.endDate(),
                response.salesAmount(),
                response.receiptAmount(),
                response.closingAmount(),
                response.status(),
                response.deletedFlag(),
                response.remark(),
                entity.getItems().stream().map(this::toItemResponse).toList(),
                entity.getCustomerId()
        );
    }

    private CustomerStatementItemResponse toItemResponse(CustomerStatementItem item) {
        return new CustomerStatementItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getSourceSalesOrderItemId(),
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
                item.getUnitPrice(),
                item.getAmount(),
                item.getCustomerId(),
                item.getProjectId(),
                item.getMaterialId(),
                item.getWarehouseId(),
                item.getBatchNoNormalized()
        );
    }
}
