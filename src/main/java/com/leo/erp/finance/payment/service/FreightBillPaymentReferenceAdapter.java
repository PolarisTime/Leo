package com.leo.erp.finance.payment.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.logistics.api.FreightBillPaymentReferenceQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class FreightBillPaymentReferenceAdapter implements FreightBillPaymentReferenceQuery {

    private final PaymentAllocationRepository paymentAllocationRepository;

    public FreightBillPaymentReferenceAdapter(PaymentAllocationRepository paymentAllocationRepository) {
        this.paymentAllocationRepository = paymentAllocationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSettledPaymentReferences(Collection<Long> statementIds) {
        Set<Long> stableStatementIds = statementIds == null
                ? Set.of()
                : statementIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (stableStatementIds.isEmpty()) {
            return false;
        }
        return paymentAllocationRepository.countSettledAllocationsByStatementIdsAndBusinessTypeAndStatus(
                stableStatementIds,
                PaymentAllocationService.FREIGHT_PAYMENT_TYPE,
                StatusConstants.AUDITED
        ) > 0;
    }
}
