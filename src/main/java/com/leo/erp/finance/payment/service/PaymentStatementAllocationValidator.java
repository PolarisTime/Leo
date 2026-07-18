package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class PaymentStatementAllocationValidator {

    private final PaymentAllocationRepository paymentAllocationRepository;
    private final FreightStatementQueryService freightStatementQueryService;

    public PaymentStatementAllocationValidator(PaymentAllocationRepository paymentAllocationRepository,
                                               FreightStatementQueryService freightStatementQueryService) {
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.freightStatementQueryService = freightStatementQueryService;
    }

    ValidatedStatement validate(PaymentRequest request,
                                String normalizedStatus,
                                Long currentPaymentId,
                                Long sourceStatementId,
                                BigDecimal allocatedAmount,
                                Map<Long, BigDecimal> requestAllocatedAmountMap,
                                int lineNo) {
        if (!PaymentAllocationService.FREIGHT_PAYMENT_TYPE.equals(request.businessType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅物流商对账单支持付款核销");
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

    private FreightStatement requireAccessibleFreightStatement(Long statementId) {
        FreightStatement statement = freightStatementQueryService.requireActiveById(statementId);
        return statement;
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
        Long carrierId = requireCounterpartyId(statement.getCarrierId(), lineNo, "物流商");
        requireRequestedCounterpartyId(request.counterpartyId(), carrierId, lineNo, "物流商");
        ValidatedStatement validatedStatement = validatedStatement(
                PaymentAllocationService.FREIGHT_PAYMENT_TYPE,
                carrierId,
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

    private ValidatedStatement validatedStatement(String counterpartyType,
                                                   Long counterpartyId,
                                                   String counterpartyCode,
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
                counterpartyType,
                counterpartyId,
                BusinessDocumentValidator.trimToNull(counterpartyCode),
                settlementCompanyId,
                normalizedCompanyName
        );
    }

    private Long requireCounterpartyId(Long counterpartyId, int lineNo, String label) {
        if (counterpartyId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行对账单缺少" + label + "ID");
        }
        return counterpartyId;
    }

    private void requireRequestedCounterpartyId(Long requestedId,
                                                Long sourceId,
                                                int lineNo,
                                                String label) {
        if (requestedId != null && !requestedId.equals(sourceId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行对账单" + label + "ID与付款单往来方ID不一致");
        }
    }

    record ValidatedStatement(
            String counterpartyType,
            Long counterpartyId,
            String counterpartyCode,
            Long settlementCompanyId,
            String settlementCompanyName
    ) {
        ValidatedStatement(String counterpartyCode,
                           Long settlementCompanyId,
                           String settlementCompanyName) {
            this(null, null, counterpartyCode, settlementCompanyId, settlementCompanyName);
        }
    }
}
