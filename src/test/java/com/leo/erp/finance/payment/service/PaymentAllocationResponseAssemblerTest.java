package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentAllocationResponseAssemblerTest {

    @Test
    void shouldUseSupplierStatementClosingAmountForSupplierPaymentResponse() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        PaymentAllocationResponseAssembler assembler = new PaymentAllocationResponseAssembler(
                supplierQueryService,
                mock(FreightStatementQueryService.class)
        );
        SupplierStatement statement = new SupplierStatement();
        statement.setId(11L);
        statement.setStatementNo("GYS-DZ-001");
        statement.setClosingAmount(new BigDecimal("1200.00"));
        when(supplierQueryService.findActiveById(11L)).thenReturn(Optional.of(statement));

        var response = assembler.toResponses(payment("供应商", allocation(101L, 1, 11L, "300.00"))).get(0);

        assertThat(response.statementNo()).isEqualTo("GYS-DZ-001");
        assertThat(response.statementBalanceAmount()).isEqualByComparingTo("1200.00");
        assertThat(response.allocatedAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void shouldUseFreightStatementUnpaidAmountForFreightPaymentResponse() {
        FreightStatementQueryService freightQueryService = mock(FreightStatementQueryService.class);
        PaymentAllocationResponseAssembler assembler = new PaymentAllocationResponseAssembler(
                mock(SupplierStatementQueryService.class),
                freightQueryService
        );
        FreightStatement statement = new FreightStatement();
        statement.setId(31L);
        statement.setStatementNo("WL-DZ-001");
        statement.setUnpaidAmount(new BigDecimal("800.00"));
        when(freightQueryService.findActiveById(31L)).thenReturn(Optional.of(statement));

        var response = assembler.toResponses(payment("物流商", allocation(301L, 1, 31L, "200.00"))).get(0);

        assertThat(response.statementNo()).isEqualTo("WL-DZ-001");
        assertThat(response.statementBalanceAmount()).isEqualByComparingTo("800.00");
        assertThat(response.allocatedAmount()).isEqualByComparingTo("200.00");
    }

    private Payment payment(String businessType, PaymentAllocation allocation) {
        Payment payment = new Payment();
        payment.setBusinessType(businessType);
        payment.getItems().add(allocation);
        allocation.setPayment(payment);
        return payment;
    }

    private PaymentAllocation allocation(Long id, Integer lineNo, Long sourceStatementId, String allocatedAmount) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setId(id);
        allocation.setLineNo(lineNo);
        allocation.setSourceStatementId(sourceStatementId);
        allocation.setAllocatedAmount(new BigDecimal(allocatedAmount));
        return allocation;
    }
}
