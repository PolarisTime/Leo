package com.leo.erp.finance.receipt.service;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationResponse;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.service.CustomerStatementQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ReceiptAllocationResponseAssembler {

    private final CustomerStatementQueryService customerStatementQueryService;

    public ReceiptAllocationResponseAssembler(CustomerStatementQueryService customerStatementQueryService) {
        this.customerStatementQueryService = customerStatementQueryService;
    }

    List<ReceiptAllocationResponse> toResponses(Receipt entity) {
        return entity.getItems().stream()
                .map(this::toResponse)
                .toList();
    }

    private ReceiptAllocationResponse toResponse(ReceiptAllocation item) {
        CustomerStatement statement = findCustomerStatement(item.getSourceStatementId());
        return new ReceiptAllocationResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceStatementId(),
                statement == null ? null : statement.getStatementNo(),
                statement == null ? null : statement.getProjectName(),
                statement == null ? BigDecimal.ZERO : statement.getClosingAmount(),
                item.getAllocatedAmount()
        );
    }

    private CustomerStatement findCustomerStatement(Long statementId) {
        return statementId == null ? null : customerStatementQueryService.findActiveById(statementId).orElse(null);
    }
}
