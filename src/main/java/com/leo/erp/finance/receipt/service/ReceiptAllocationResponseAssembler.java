package com.leo.erp.finance.receipt.service;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationResponse;
import com.leo.erp.statement.api.CustomerStatementApi;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ReceiptAllocationResponseAssembler {

    private final CustomerStatementApi customerStatementApi;

    public ReceiptAllocationResponseAssembler(CustomerStatementApi customerStatementApi) {
        this.customerStatementApi = customerStatementApi;
    }

    List<ReceiptAllocationResponse> toResponses(Receipt entity) {
        return entity.getItems().stream()
                .map(this::toResponse)
                .toList();
    }

    private ReceiptAllocationResponse toResponse(ReceiptAllocation item) {
        CustomerStatementApi.Snapshot statement = findCustomerStatement(item.getSourceStatementId());
        return new ReceiptAllocationResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceStatementId(),
                statement == null ? null : statement.statementNo(),
                statement == null ? null : statement.projectName(),
                statement == null ? BigDecimal.ZERO : statement.closingAmount(),
                item.getAllocatedAmount()
        );
    }

    private CustomerStatementApi.Snapshot findCustomerStatement(Long statementId) {
        return statementId == null ? null : customerStatementApi.findActiveById(statementId).orElse(null);
    }
}
