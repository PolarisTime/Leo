package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.stereotype.Service;

import java.util.function.LongSupplier;

@Service
public class ReceiptApplyService {

    private static final String RECEIPT_STATUS_SETTLED = StatusConstants.RECEIVED;

    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final ReceiptAllocationService receiptAllocationService;
    private final ReceiptSettlementSyncService settlementSyncService;
    private final ReceiptPartyIdentityResolver partyIdentityResolver;

    public ReceiptApplyService(WorkflowTransitionGuard workflowTransitionGuard,
                               ReceiptAllocationService receiptAllocationService,
                               ReceiptSettlementSyncService settlementSyncService,
                               ReceiptPartyIdentityResolver partyIdentityResolver) {
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.receiptAllocationService = receiptAllocationService;
        this.settlementSyncService = settlementSyncService;
        this.partyIdentityResolver = partyIdentityResolver;
    }

    void apply(Receipt entity, ReceiptRequest request, LongSupplier nextIdSupplier) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "收款单状态",
                StatusConstants.ALLOWED_RECEIPT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "receipt",
                entity.getStatus(),
                nextStatus,
                RECEIPT_STATUS_SETTLED
        );
        settlementSyncService.captureOriginalAllocationStatementIds(entity);
        entity.setReceiptNo(request.receiptNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setCustomerCode(BusinessDocumentValidator.trimToNull(request.customerCode()));
        entity.setProjectId(request.projectId());
        entity.setReceiptDate(request.receiptDate());
        entity.setPayType(request.payType());
        entity.setAmount(TradeItemCalculator.scaleAmount(request.amount()));
        entity.setStatus(nextStatus);
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
        ReceiptAllocationService.AllocationApplyResult allocationResult =
                receiptAllocationService.applyAllocations(entity, request, nextStatus, nextIdSupplier);
        ReceiptPartyIdentityResolver.PartySnapshot partySnapshot = allocationResult.allocationEmpty()
                ? partyIdentityResolver.resolve(request)
                : new ReceiptPartyIdentityResolver.PartySnapshot(
                        allocationResult.customerId(),
                        request.customerCode(),
                        request.customerName(),
                        allocationResult.projectId(),
                        request.projectName()
                );
        entity.setCustomerId(partySnapshot.customerId());
        entity.setCustomerName(partySnapshot.customerName());
        entity.setProjectId(partySnapshot.projectId());
        entity.setProjectName(partySnapshot.projectName());
        entity.setCustomerCode(receiptAllocationService.mergeCustomerCode(
                partySnapshot.customerCode(),
                allocationResult.customerCode()
        ));
        entity.setSettlementCompanyId(allocationResult.settlementCompanyId());
        entity.setSettlementCompanyName(allocationResult.settlementCompanyName());
        entity.setSourceStatementId(settlementSyncService.resolveLegacySourceStatementId(entity));
    }
}
