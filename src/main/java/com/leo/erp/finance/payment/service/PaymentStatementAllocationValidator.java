package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class PaymentStatementAllocationValidator {

    private final PaymentAllocationRepository paymentAllocationRepository;
    private final SupplierStatementQueryService supplierStatementQueryService;
    private final FreightStatementQueryService freightStatementQueryService;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;

    public PaymentStatementAllocationValidator(PaymentAllocationRepository paymentAllocationRepository,
                                               SupplierStatementQueryService supplierStatementQueryService,
                                               FreightStatementQueryService freightStatementQueryService,
                                               ResourceRecordAccessGuard resourceRecordAccessGuard) {
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.supplierStatementQueryService = supplierStatementQueryService;
        this.freightStatementQueryService = freightStatementQueryService;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
    }

    ValidatedStatement validate(PaymentRequest request,
                                String normalizedStatus,
                                Long currentPaymentId,
                                Long sourceStatementId,
                                BigDecimal allocatedAmount,
                                Map<Long, BigDecimal> requestAllocatedAmountMap,
                                int lineNo) {
        if (PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(request.businessType())) {
            return validateSupplierStatement(
                    request,
                    normalizedStatus,
                    currentPaymentId,
                    requireAccessibleSupplierStatement(sourceStatementId),
                    allocatedAmount,
                    requestAllocatedAmountMap,
                    lineNo
            );
        }
        return validateFreightStatement(
                request,
                normalizedStatus,
                currentPaymentId,
                requireAccessibleFreightStatement(sourceStatementId),
                allocatedAmount,
                requestAllocatedAmountMap,
                lineNo
        );
    }

    private SupplierStatement requireAccessibleSupplierStatement(Long statementId) {
        SupplierStatement statement = supplierStatementQueryService.requireActiveById(statementId);
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "supplier-statement",
                ResourcePermissionCatalog.READ,
                statement
        );
        return statement;
    }

    private FreightStatement requireAccessibleFreightStatement(Long statementId) {
        FreightStatement statement = freightStatementQueryService.requireActiveById(statementId);
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "freight-statement",
                ResourcePermissionCatalog.READ,
                statement
        );
        return statement;
    }

    private ValidatedStatement validateSupplierStatement(PaymentRequest request,
                                                         String normalizedStatus,
                                                         Long currentPaymentId,
                                                         SupplierStatement statement,
                                                         BigDecimal allocatedAmount,
                                                         Map<Long, BigDecimal> requestAllocatedAmountMap,
                                                         int lineNo) {
        String statementSupplierCode = BusinessDocumentValidator.trimToNull(statement.getSupplierCode());
        if (statementSupplierCode == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行对账单供应商编码不能为空，不能付款核销");
        }
        String requestSupplierCode = BusinessDocumentValidator.trimToNull(request.counterpartyCode());
        if (requestSupplierCode != null && !requestSupplierCode.equals(statementSupplierCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行对账单供应商编码与付款单往来单位编码不一致");
        }
        ValidatedStatement validatedStatement = validatedStatement(
                statementSupplierCode,
                statement.getSettlementCompanyId(),
                statement.getSettlementCompanyName(),
                lineNo
        );
        if (requestAllocatedAmountMap.containsKey(statement.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一付款单不能重复核销同一供应商对账单");
        }
        if (PaymentAllocationService.PAYMENT_STATUS_SETTLED.equals(normalizedStatus)) {
            BusinessDocumentValidator.requireStatusIn(
                    statement.getStatus(),
                    StatusConstants.SETTLEABLE_SUPPLIER_STATEMENT_STATUS,
                    "第" + lineNo + "行供应商对账单未确认，不能付款"
            );
            BigDecimal settledAmount = TradeItemCalculator.safeBigDecimal(
                    paymentAllocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                            statement.getId(),
                            PaymentAllocationService.SUPPLIER_PAYMENT_TYPE,
                            PaymentAllocationService.PAYMENT_STATUS_SETTLED,
                            currentPaymentId
                    )
            );
            BigDecimal nextSettledAmount = settledAmount.add(allocatedAmount);
            if (nextSettledAmount.compareTo(statement.getPurchaseAmount()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行关联供应商对账单累计付款金额不能超过采购金额");
            }
        }
        requestAllocatedAmountMap.put(statement.getId(), allocatedAmount);
        return validatedStatement;
    }

    private ValidatedStatement validateFreightStatement(PaymentRequest request,
                                                        String normalizedStatus,
                                                        Long currentPaymentId,
                                                        FreightStatement statement,
                                                        BigDecimal allocatedAmount,
                                                        Map<Long, BigDecimal> requestAllocatedAmountMap,
                                                        int lineNo) {
        BusinessDocumentValidator.requireSameText(
                request.counterpartyName(),
                statement.getCarrierName(),
                "第" + lineNo + "行对账单物流商与付款单往来单位不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.counterpartyCode(),
                statement.getCarrierCode(),
                "第" + lineNo + "行对账单物流商编码与付款单往来单位编码不一致"
        );
        ValidatedStatement validatedStatement = validatedStatement(
                statement.getCarrierCode(),
                statement.getSettlementCompanyId(),
                statement.getSettlementCompanyName(),
                lineNo
        );
        if (requestAllocatedAmountMap.containsKey(statement.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一付款单不能重复核销同一物流对账单");
        }
        if (PaymentAllocationService.PAYMENT_STATUS_SETTLED.equals(normalizedStatus)) {
            BusinessDocumentValidator.requireStatusIn(
                    statement.getStatus(),
                    StatusConstants.SETTLEABLE_FREIGHT_STATEMENT_STATUS,
                    "第" + lineNo + "行物流对账单未审核，不能付款"
            );
            BigDecimal settledAmount = TradeItemCalculator.safeBigDecimal(
                    paymentAllocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                            statement.getId(),
                            PaymentAllocationService.FREIGHT_PAYMENT_TYPE,
                            PaymentAllocationService.PAYMENT_STATUS_SETTLED,
                            currentPaymentId
                    )
            );
            BigDecimal nextSettledAmount = settledAmount.add(allocatedAmount);
            if (nextSettledAmount.compareTo(statement.getTotalFreight()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行关联物流对账单累计付款金额不能超过总运费");
            }
        }
        requestAllocatedAmountMap.put(statement.getId(), allocatedAmount);
        return validatedStatement;
    }

    private ValidatedStatement validatedStatement(String counterpartyCode,
                                                   Long settlementCompanyId,
                                                   String settlementCompanyName,
                                                   int lineNo) {
        if (settlementCompanyId == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行对账单结算主体不能为空，不能付款核销"
            );
        }
        String normalizedCompanyName = BusinessDocumentValidator.trimToNull(settlementCompanyName);
        if (normalizedCompanyName == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行对账单结算主体名称不能为空，不能付款核销"
            );
        }
        return new ValidatedStatement(
                BusinessDocumentValidator.trimToNull(counterpartyCode),
                settlementCompanyId,
                normalizedCompanyName
        );
    }

    record ValidatedStatement(
            String counterpartyCode,
            Long settlementCompanyId,
            String settlementCompanyName
    ) {
    }
}
