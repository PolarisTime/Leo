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
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.LongSupplier;

@Service
public class PaymentApplyService {

    private final PaymentAllocationService paymentAllocationService;
    private final PaymentSettlementSyncService settlementSyncService;
    private final PaymentPurchasePrepaymentService purchasePrepaymentService;
    private SupplierRepository supplierRepository;
    private CompanySettingRepository companySettingRepository;

    public PaymentApplyService(PaymentAllocationService paymentAllocationService,
                               PaymentSettlementSyncService settlementSyncService,
                               PaymentPurchasePrepaymentService purchasePrepaymentService) {
        this.paymentAllocationService = paymentAllocationService;
        this.settlementSyncService = settlementSyncService;
        this.purchasePrepaymentService = purchasePrepaymentService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setSupplierDependencies(SupplierRepository supplierRepository,
                                 CompanySettingRepository companySettingRepository) {
        this.supplierRepository = supplierRepository;
        this.companySettingRepository = companySettingRepository;
    }

    void apply(Payment entity, PaymentRequest request, LongSupplier nextIdSupplier) {
        String paymentPurpose = PaymentPurposes.normalize(request.paymentPurpose());
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "付款单状态",
                StatusConstants.ALLOWED_PAYMENT_STATUS
        );
        assertStatusNotChangedBySave(entity, nextStatus);
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
        if (PaymentPurposes.SUPPLIER_PAYMENT.equals(paymentPurpose)) {
            applySupplierPayment(entity, request);
            return;
        }
        if (PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(request.businessType())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "供应商付款已统一为总额付款，不允许关联供应商对账单"
            );
        }
        applyStatementSettlement(entity, request, nextStatus, nextIdSupplier);
    }

    private void assertStatusNotChangedBySave(Payment entity, String requestedStatus) {
        String currentStatus = entity.getStatus();
        if (currentStatus == null) {
            if (!StatusConstants.DRAFT.equals(requestedStatus)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "新建付款单只能保存为草稿，审核请使用状态接口"
                );
            }
            return;
        }
        if (!currentStatus.equals(requestedStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "普通保存不能修改付款单状态，请使用状态接口"
            );
        }
    }

    private void applySupplierPayment(Payment entity, PaymentRequest request) {
        if (!PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(request.businessType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商总额付款的往来类型必须为供应商");
        }
        if (TradeItemCalculator.safeBigDecimal(entity.getAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "付款金额必须大于0");
        }
        if (request.counterpartyId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商ID不能为空");
        }
        if (request.settlementCompanyId() == null
                || BusinessDocumentValidator.trimToNull(request.settlementCompanyName()) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算主体不能为空");
        }
        if (request.sourceStatementId() != null || request.sourcePurchaseOrderId() != null
                || request.items() != null && !request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商总额付款不能关联采购或对账明细");
        }
        if (supplierRepository == null || companySettingRepository == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商付款身份服务不可用");
        }
        Supplier supplier = supplierRepository.findByIdAndDeletedFlagFalse(request.counterpartyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商不存在"));
        BusinessDocumentValidator.requireSameText(
                request.counterpartyName(), supplier.getSupplierName(), "供应商名称与ID不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.counterpartyCode(), supplier.getSupplierCode(), "供应商编码与ID不一致"
        );
        CompanySetting company = companySettingRepository
                .findByIdAndDeletedFlagFalse(request.settlementCompanyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "结算主体不存在"));
        BusinessDocumentValidator.requireSameText(
                request.settlementCompanyName(), company.getCompanyName(), "结算主体名称与ID不一致"
        );
        entity.setCounterpartyId(supplier.getId());
        entity.setCounterpartyName(supplier.getSupplierName());
        entity.setCounterpartyCode(BusinessDocumentValidator.trimToNull(supplier.getSupplierCode()));
        entity.setSupplierCode(entity.getCounterpartyCode());
        entity.setSupplierName(entity.getCounterpartyName());
        entity.setSettlementCompanyId(company.getId());
        entity.setSettlementCompanyName(company.getCompanyName());
        entity.setSourceStatementId(null);
        entity.setSourcePurchaseOrderId(null);
        entity.setPurchaseOrderNo(null);
        entity.getItems().clear();
    }

    private void applyPurchasePrepayment(Payment entity,
                                         PaymentRequest request,
                                         String nextStatus) {
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "采购预付款已统一为供应商总额付款形成的预付款余额，不允许新建或修改"
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
