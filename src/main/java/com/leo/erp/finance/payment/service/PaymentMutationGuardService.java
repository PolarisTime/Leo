package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.common.service.SupplierLedgerLockService;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

@Service
public class PaymentMutationGuardService {

    private final PaymentRepository paymentRepository;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final SupplierLedgerLockService supplierLedgerLockService;
    private final PaymentSettlementSyncService settlementSyncService;
    private final PaymentAllocationService paymentAllocationService;
    private final PaymentPurchasePrepaymentService purchasePrepaymentService;

    public PaymentMutationGuardService(PaymentRepository paymentRepository,
                                       SourceAllocationLockService sourceAllocationLockService,
                                       SupplierLedgerLockService supplierLedgerLockService,
                                       PaymentSettlementSyncService settlementSyncService,
                                       PaymentAllocationService paymentAllocationService,
                                       PaymentPurchasePrepaymentService purchasePrepaymentService) {
        this.paymentRepository = paymentRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.supplierLedgerLockService = supplierLedgerLockService;
        this.settlementSyncService = settlementSyncService;
        this.paymentAllocationService = paymentAllocationService;
        this.purchasePrepaymentService = purchasePrepaymentService;
    }

    void lockRoot(Long id) {
        paymentRepository.findByIdAndDeletedFlagFalseForUpdate(id);
    }

    void assertUpdateAllowed(Payment entity, String operation) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核付款单禁止修改");
        }
        assertLegacyPaymentReadOnly(entity, operation);
    }

    void assertLegacyPaymentReadOnly(Payment entity, String operation) {
        boolean purchasePrepayment = PaymentPurposes.isPurchasePrepayment(entity.getPaymentPurpose());
        boolean supplierStatementSettlement = PaymentPurposes.STATEMENT_SETTLEMENT.equals(
                PaymentPurposes.normalize(entity.getPaymentPurpose())
        ) && PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(entity.getBusinessType());
        if (purchasePrepayment || supplierStatementSettlement) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "旧采购预付款及供应商对账付款仅供历史查询，不允许" + operation
            );
        }
    }

    void assertStatusTransitionAllowed(Payment entity, String currentStatus, String nextStatus) {
        if (StatusConstants.AUDITED.equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核付款单禁止反审核");
        }
        assertLegacyPaymentReadOnly(entity, "审核");
        if (PaymentPurposes.isSupplierTotalPayment(entity.getPaymentPurpose())) {
            if (PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(entity.getCounterpartyType())) {
                lockSupplierLedgerMutation(entity);
            } else if (entity.getCounterpartyId() == null || entity.getSettlementCompanyId() == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流付款缺少物流商或结算主体身份");
            }
            return;
        }
        if (PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(entity.getCounterpartyType())) {
            lockSupplierLedgerMutation(entity);
        }
        lockAllocationStatements(entity, null);
        settlementSyncService.captureOriginalAllocationState(entity);
        paymentAllocationService.validateExistingAllocationsForSettlement(entity, nextStatus);
    }

    void assertDeletable(Payment entity) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核付款单禁止删除");
        }
        if (StatusConstants.LEGACY_PAID.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "历史已付款单据仅供查询，不允许删除");
        }
        assertLegacyPaymentReadOnly(entity, "删除");
        if (PaymentPurposes.isPurchasePrepayment(entity.getPaymentPurpose())) {
            purchasePrepaymentService.validateNoStatementAllocations(entity);
            return;
        }
        lockAllocationStatements(entity, null);
    }

    void lockAllocationStatements(Payment entity, PaymentRequest request) {
        TreeSet<Long> freightStatementIds = new TreeSet<>();
        if (entity != null
                && !PaymentPurposes.isPurchasePrepayment(entity.getPaymentPurpose())
                && !PaymentPurposes.isSupplierTotalPayment(entity.getPaymentPurpose())
                && PaymentAllocationService.FREIGHT_PAYMENT_TYPE.equals(entity.getBusinessType())) {
            freightStatementIds.addAll(existingAllocationStatementIds(entity));
        }
        if (request != null
                && !PaymentPurposes.isPurchasePrepayment(request.paymentPurpose())
                && !PaymentPurposes.isSupplierTotalPayment(request.paymentPurpose())
                && PaymentAllocationService.FREIGHT_PAYMENT_TYPE.equals(request.businessType())) {
            freightStatementIds.addAll(requestedAllocationStatementIds(request));
        }
        sourceAllocationLockService.lockStatementSources(
                List.of(),
                List.copyOf(freightStatementIds)
        );
    }

    private List<Long> existingAllocationStatementIds(Payment entity) {
        if (entity.getItems() != null && !entity.getItems().isEmpty()) {
            return entity.getItems().stream()
                    .map(item -> item.getSourceFreightStatementId() == null
                            ? item.getSourceStatementId()
                            : item.getSourceFreightStatementId())
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return entity.getSourceStatementId() == null
                ? List.of()
                : List.of(entity.getSourceStatementId());
    }

    private List<Long> requestedAllocationStatementIds(PaymentRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            return request.items().stream()
                    .map(item -> item.sourceFreightStatementId() == null
                            ? item.sourceStatementId()
                            : item.sourceFreightStatementId())
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return request.sourceStatementId() == null
                ? List.of()
                : List.of(request.sourceStatementId());
    }

    private void lockSupplierLedgerMutation(Payment entity) {
        if (entity.getCounterpartyId() == null || entity.getSettlementCompanyId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商付款缺少供应商或结算主体身份");
        }
        supplierLedgerLockService.lock(
                entity.getSettlementCompanyId(),
                entity.getCounterpartyId()
        );
    }
}
