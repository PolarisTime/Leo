package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptPurposes;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import org.springframework.stereotype.Service;

import java.util.function.LongSupplier;
import java.math.BigDecimal;

@Service
public class ReceiptApplyService {

    private final ReceiptAllocationService receiptAllocationService;
    private final ReceiptSettlementSyncService settlementSyncService;
    private final ReceiptPartyIdentityResolver partyIdentityResolver;

    public ReceiptApplyService(ReceiptAllocationService receiptAllocationService,
                               ReceiptSettlementSyncService settlementSyncService,
                               ReceiptPartyIdentityResolver partyIdentityResolver) {
        this.receiptAllocationService = receiptAllocationService;
        this.settlementSyncService = settlementSyncService;
        this.partyIdentityResolver = partyIdentityResolver;
    }

    void apply(Receipt entity, ReceiptRequest request, LongSupplier nextIdSupplier) {
        String receiptPurpose = ReceiptPurposes.normalize(request.receiptPurpose());
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "收款单状态",
                StatusConstants.ALLOWED_RECEIPT_STATUS
        );
        settlementSyncService.captureOriginalAllocationStatementIds(entity);
        entity.setReceiptNo(request.receiptNo());
        entity.setReceiptPurpose(receiptPurpose);
        entity.setReceiptDate(request.receiptDate());
        entity.setPayType(request.payType());
        entity.setAmount(TradeItemCalculator.scaleAmount(request.amount()));
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
        if (TradeItemCalculator.safeBigDecimal(entity.getAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.VALIDATION_ERROR,
                    "收款金额必须大于0"
            );
        }
        if (ReceiptPurposes.isSupplierReceipt(receiptPurpose)) {
            applySupplierReceipt(entity, request);
        } else {
            applyCustomerReceipt(entity, request, nextStatus, nextIdSupplier);
        }
        assertStatusNotChangedBySave(entity, nextStatus);
        entity.setStatus(nextStatus);
    }

    private void assertStatusNotChangedBySave(Receipt entity, String requestedStatus) {
        String currentStatus = entity.getStatus();
        if (currentStatus == null) {
            if (!StatusConstants.DRAFT.equals(requestedStatus)) {
                throw new com.leo.erp.common.error.BusinessException(
                        com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                        "新建收款单只能保存为草稿，审核请使用状态接口"
                );
            }
            return;
        }
        if (!currentStatus.equals(requestedStatus)) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "普通保存不能修改收款单状态，请使用状态接口"
            );
        }
    }

    private void applyCustomerReceipt(Receipt entity,
                                      ReceiptRequest request,
                                      String nextStatus,
                                      LongSupplier nextIdSupplier) {
        if (request.counterpartyType() != null && !"客户".equals(request.counterpartyType())) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.VALIDATION_ERROR,
                    "客户收款的往来类型必须为客户"
            );
        }
        entity.setCounterpartyType("客户");
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setCustomerCode(BusinessDocumentValidator.trimToNull(request.customerCode()));
        entity.setProjectId(request.projectId());
        ReceiptAllocationService.AllocationApplyResult allocationResult =
                receiptAllocationService.applyAllocations(entity, request, nextStatus, nextIdSupplier);
        ReceiptPartyIdentityResolver.PartySnapshot partySnapshot = allocationResult.allocationEmpty()
                ? partyIdentityResolver.resolve(request)
                : new ReceiptPartyIdentityResolver.PartySnapshot(
                        allocationResult.customerId(),
                        request.customerCode(),
                        request.customerName(),
                        allocationResult.projectId(),
                        request.projectName(),
                        allocationResult.settlementCompanyId(),
                        allocationResult.settlementCompanyName()
                );
        entity.setCustomerId(partySnapshot.customerId());
        entity.setCustomerName(partySnapshot.customerName());
        entity.setProjectId(partySnapshot.projectId());
        entity.setProjectName(partySnapshot.projectName());
        entity.setCustomerCode(receiptAllocationService.mergeCustomerCode(
                partySnapshot.customerCode(),
                allocationResult.customerCode()
        ));
        entity.setCounterpartyId(entity.getCustomerId());
        entity.setCounterpartyCode(entity.getCustomerCode());
        entity.setCounterpartyName(entity.getCustomerName());
        entity.setSettlementCompanyId(partySnapshot.settlementCompanyId());
        entity.setSettlementCompanyName(partySnapshot.settlementCompanyName());
        entity.setSourceStatementId(settlementSyncService.resolveLegacySourceStatementId(entity));
    }

    private void applySupplierReceipt(Receipt entity, ReceiptRequest request) {
        if (!"供应商".equals(request.counterpartyType())) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.VALIDATION_ERROR,
                    "供应商资金收款的往来类型必须为供应商"
            );
        }
        if (request.sourceStatementId() != null || request.items() != null && !request.items().isEmpty()) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.VALIDATION_ERROR,
                    "供应商资金收款不能关联客户对账单"
            );
        }
        ReceiptPartyIdentityResolver.SupplierPartySnapshot party = partyIdentityResolver.resolveSupplier(request);
        entity.setCounterpartyType("供应商");
        entity.setCounterpartyId(party.supplierId());
        entity.setCounterpartyCode(party.supplierCode());
        entity.setCounterpartyName(party.supplierName());
        entity.setSettlementCompanyId(party.settlementCompanyId());
        entity.setSettlementCompanyName(party.settlementCompanyName());
        entity.setCustomerId(null);
        entity.setCustomerCode(null);
        entity.setCustomerName(null);
        entity.setProjectId(null);
        entity.setProjectName(null);
        entity.setSourceStatementId(null);
        entity.getItems().clear();
    }
}
