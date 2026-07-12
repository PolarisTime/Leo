package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PaymentSettlementSyncService {

    private final ApplicationEventPublisher eventPublisher;

    public PaymentSettlementSyncService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    void captureOriginalAllocationState(Payment entity) {
        entity.setOriginalBusinessType(entity.getBusinessType());
        entity.setOriginalAllocationStatementIds(collectStatementIds(entity.getBusinessType(), entity.getItems()));
    }

    Long resolveLegacySourceStatementId(Payment entity) {
        return entity.getItems().size() == 1
                ? statementId(entity.getBusinessType(), entity.getItems().get(0))
                : null;
    }

    void syncLinkedStatements(Payment entity) {
        Set<StatementLink> links = new LinkedHashSet<>();
        if (entity.getOriginalBusinessType() != null) {
            entity.getOriginalAllocationStatementIds()
                    .forEach(statementId -> links.add(new StatementLink(entity.getOriginalBusinessType(), statementId)));
        }
        entity.getItems()
                .forEach(item -> links.add(new StatementLink(
                        entity.getBusinessType(),
                        statementId(entity.getBusinessType(), item)
                )));
        for (StatementLink link : links) {
            if (link.statementId() == null || link.businessType() == null) {
                continue;
            }
            eventPublisher.publishEvent(new PaymentSettledEvent(link.statementId(), link.businessType()));
        }
    }

    private Set<Long> collectStatementIds(String businessType, List<PaymentAllocation> items) {
        Set<Long> statementIds = new LinkedHashSet<>();
        for (PaymentAllocation item : items) {
            Long statementId = statementId(businessType, item);
            if (statementId != null) {
                statementIds.add(statementId);
            }
        }
        return statementIds;
    }

    private Long statementId(String businessType, PaymentAllocation item) {
        Long typedId = PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(businessType)
                ? item.getSourceSupplierStatementId()
                : item.getSourceFreightStatementId();
        return typedId == null ? item.getSourceStatementId() : typedId;
    }

    private record StatementLink(String businessType, Long statementId) {
    }
}
