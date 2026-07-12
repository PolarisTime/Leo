package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.LongSupplier;

@Service
public class PaymentApplyService {

    private static final String PAYMENT_STATUS_SETTLED = StatusConstants.PAID;

    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final PaymentAllocationService paymentAllocationService;
    private final PaymentSettlementSyncService settlementSyncService;
    private final PaymentPurchasePrepaymentService purchasePrepaymentService;

    public PaymentApplyService(WorkflowTransitionGuard workflowTransitionGuard,
                               PaymentAllocationService paymentAllocationService,
                               PaymentSettlementSyncService settlementSyncService,
                               PaymentPurchasePrepaymentService purchasePrepaymentService) {
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.paymentAllocationService = paymentAllocationService;
        this.settlementSyncService = settlementSyncService;
        this.purchasePrepaymentService = purchasePrepaymentService;
    }

    void apply(Payment entity, PaymentRequest request, LongSupplier nextIdSupplier) {
        String paymentPurpose = PaymentPurposes.normalize(request.paymentPurpose());
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
        entity.setCounterpartyType(request.businessType());
        entity.setCounterpartyId(request.counterpartyId());
        entity.setPaymentPurpose(paymentPurpose);
        entity.setPaymentDate(request.paymentDate());
        entity.setPayType(request.payType());
        entity.setAmount(TradeItemCalculator.scaleAmount(request.amount()));
        entity.setStatus(nextStatus);
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
        if (PaymentPurposes.PURCHASE_PREPAYMENT.equals(paymentPurpose)) {
            applyPurchasePrepayment(entity, request, nextStatus);
            return;
        }
        applyStatementSettlement(entity, request, nextStatus, nextIdSupplier);
    }

    private void applyPurchasePrepayment(Payment entity,
                                         PaymentRequest request,
                                         String nextStatus) {
        if (!PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(request.businessType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购预付款业务类型必须为供应商");
        }
        if (TradeItemCalculator.safeBigDecimal(entity.getAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购预付款金额必须大于0");
        }
        if (request.sourceStatementId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购预付款不能关联对账单");
        }
        if (request.items() != null && !request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购预付款不能包含对账单核销明细");
        }
        entity.setSourceStatementId(null);
        entity.getItems().clear();
        purchasePrepaymentService.applySourceSnapshot(
                entity,
                request.sourcePurchaseOrderId(),
                entity.getAmount(),
                nextStatus
        );
    }

    private void applyStatementSettlement(Payment entity,
                                          PaymentRequest request,
                                          String nextStatus,
                                          LongSupplier nextIdSupplier) {
        clearPurchasePrepaymentSnapshot(entity);
        entity.setCounterpartyName(request.counterpartyName());
        entity.setCounterpartyCode(BusinessDocumentValidator.trimToNull(request.counterpartyCode()));
        PaymentAllocationService.AllocationApplyResult allocationResult =
                paymentAllocationService.applyAllocations(entity, request, nextStatus, nextIdSupplier);
        if (request.counterpartyId() != null
                && allocationResult.counterpartyId() != null
                && !request.counterpartyId().equals(allocationResult.counterpartyId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "付款单往来方ID与来源对账单不一致");
        }
        entity.setCounterpartyType(allocationResult.counterpartyType() == null
                ? request.businessType()
                : allocationResult.counterpartyType());
        entity.setCounterpartyId(allocationResult.counterpartyId() == null
                ? request.counterpartyId()
                : allocationResult.counterpartyId());
        entity.setCounterpartyCode(paymentAllocationService.mergeCounterpartyCode(
                entity.getCounterpartyCode(),
                allocationResult.counterpartyCode()
        ));
        entity.setSettlementCompanyId(allocationResult.settlementCompanyId());
        entity.setSettlementCompanyName(allocationResult.settlementCompanyName());
        entity.setSourceStatementId(settlementSyncService.resolveLegacySourceStatementId(entity));
    }

    private void clearPurchasePrepaymentSnapshot(Payment entity) {
        entity.setSourcePurchaseOrderId(null);
        entity.setPurchaseOrderNo(null);
        entity.setSupplierCode(null);
        entity.setSupplierName(null);
        entity.setSettlementCompanyId(null);
        entity.setSettlementCompanyName(null);
    }
}
