package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentPrepaymentAllocationUpdateRequest;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentPrepaymentAllocationServiceTest {

    @Test
    void shouldRejectAllLegacyPurchasePrepaymentAllocationWrites() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PaymentPrepaymentAllocationService service = new PaymentPrepaymentAllocationService(
                paymentRepository,
                allocationRepository,
                mock(SupplierStatementQueryService.class),
                lockService,
                mock(ResourceRecordAccessGuard.class),
                mock(SnowflakeIdGenerator.class),
                mock(PaymentSettlementSyncService.class),
                mock(PaymentAllocationResponseAssembler.class)
        );

        assertThatThrownBy(() -> service.replaceAllocations(
                5L,
                new PaymentPrepaymentAllocationUpdateRequest(List.of())
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款核销已停用");

        verify(paymentRepository, never()).findByIdAndDeletedFlagFalseForUpdate(any());
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(lockService, never()).lockStatementSources(any(), any(), any());
    }
}
