package com.leo.erp.finance.payment.service;

import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.stereotype.Service;

import java.util.function.LongSupplier;

@Service
public class PaymentApplyService {

    private static final String PAYMENT_STATUS_SETTLED = StatusConstants.PAID;

    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final PaymentAllocationService paymentAllocationService;
    private final PaymentSettlementSyncService settlementSyncService;

    public PaymentApplyService(WorkflowTransitionGuard workflowTransitionGuard,
                               PaymentAllocationService paymentAllocationService,
                               PaymentSettlementSyncService settlementSyncService) {
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.paymentAllocationService = paymentAllocationService;
        this.settlementSyncService = settlementSyncService;
    }

    void apply(Payment entity, PaymentRequest request, LongSupplier nextIdSupplier) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "付款单状态",
                StatusConstants.ALLOWED_PAYMENT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "payment",
                entity.getStatus(),
                nextStatus,
                PAYMENT_STATUS_SETTLED
        );
        settlementSyncService.captureOriginalAllocationState(entity);
        entity.setPaymentNo(request.paymentNo());
        entity.setBusinessType(request.businessType());
        entity.setCounterpartyName(request.counterpartyName());
        entity.setCounterpartyCode(BusinessDocumentValidator.trimToNull(request.counterpartyCode()));
        entity.setPaymentDate(request.paymentDate());
        entity.setPayType(request.payType());
        entity.setAmount(TradeItemCalculator.scaleAmount(request.amount()));
        entity.setStatus(nextStatus);
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
        PaymentAllocationService.AllocationApplyResult allocationResult =
                paymentAllocationService.applyAllocations(entity, request, nextStatus, nextIdSupplier);
        entity.setCounterpartyCode(paymentAllocationService.mergeCounterpartyCode(
                entity.getCounterpartyCode(),
                allocationResult.counterpartyCode()
        ));
        entity.setSourceStatementId(settlementSyncService.resolveLegacySourceStatementId(entity));
    }
}
