package com.leo.erp.sales.outbound.service;

import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOutboundDownstreamMutationGuardContractTest {

    @Test
    void shouldExposeRequiredDownstreamMutationContract() {
        assertThat(Arrays.stream(SalesOutboundDownstreamMutationGuard.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .contains(
                        CustomerStatementRepository.class,
                        ReceiptAllocationRepository.class,
                        FreightBillRepository.class
                );
        assertThat(Arrays.stream(SalesOutboundDownstreamMutationGuard.class.getDeclaredMethods()))
                .anySatisfy(method -> {
                    assertThat(method.getName()).isEqualTo("assertReverseAuditAllowed");
                    assertThat(method.getParameterTypes()).containsExactly(SalesOutbound.class);
                });
    }
}
