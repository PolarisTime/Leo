package com.leo.erp.finance.receipt.service;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.service.CustomerStatementQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptAllocationResponseAssemblerTest {

    @Test
    void shouldUseCustomerStatementProjectAndClosingAmountForReceiptResponse() {
        CustomerStatementQueryService customerQueryService = mock(CustomerStatementQueryService.class);
        ReceiptAllocationResponseAssembler assembler = new ReceiptAllocationResponseAssembler(customerQueryService);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(21L);
        statement.setStatementNo("KH-DZ-001");
        statement.setProjectName("项目A");
        statement.setClosingAmount(new BigDecimal("900.00"));
        when(customerQueryService.findActiveById(21L)).thenReturn(Optional.of(statement));

        var response = assembler.toResponses(receipt(allocation(201L, 1, 21L, "500.00"))).get(0);

        assertThat(response.statementNo()).isEqualTo("KH-DZ-001");
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.statementBalanceAmount()).isEqualByComparingTo("900.00");
        assertThat(response.allocatedAmount()).isEqualByComparingTo("500.00");
    }

    private Receipt receipt(ReceiptAllocation allocation) {
        Receipt receipt = new Receipt();
        receipt.getItems().add(allocation);
        allocation.setReceipt(receipt);
        return receipt;
    }

    private ReceiptAllocation allocation(Long id, Integer lineNo, Long sourceStatementId, String allocatedAmount) {
        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.setId(id);
        allocation.setLineNo(lineNo);
        allocation.setSourceStatementId(sourceStatementId);
        allocation.setAllocatedAmount(new BigDecimal(allocatedAmount));
        return allocation;
    }
}
