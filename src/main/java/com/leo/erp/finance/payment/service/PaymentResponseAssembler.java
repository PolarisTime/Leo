package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.system.company.api.SettlementAccountSnapshot;
import org.springframework.stereotype.Service;

@Service
public class PaymentResponseAssembler {

    private final PaymentMapper paymentMapper;
    private final PaymentAllocationResponseAssembler allocationResponseAssembler;

    public PaymentResponseAssembler(PaymentMapper paymentMapper,
                                    PaymentAllocationResponseAssembler allocationResponseAssembler) {
        this.paymentMapper = paymentMapper;
        this.allocationResponseAssembler = allocationResponseAssembler;
    }

    public PaymentResponse toSummaryResponse(Payment entity, SettlementAccountSnapshot account) {
        return withAccount(paymentMapper.toResponse(entity), account);
    }

    public PaymentResponse toDetailResponse(Payment entity, SettlementAccountSnapshot account) {
        PaymentResponse response = paymentMapper.toResponse(entity);
        return new PaymentResponse(
                response.id(),
                response.paymentNo(),
                response.counterpartyType(),
                response.counterpartyId(),
                response.paymentPurpose(),
                response.counterpartyCode(),
                response.counterpartyName(),
                response.sourceStatementId(),
                response.sourcePurchaseOrderId(),
                response.purchaseOrderNo(),
                response.supplierCode(),
                response.supplierName(),
                response.settlementCompanyId(),
                response.settlementCompanyName(),
                response.accountId(),
                account == null ? null : account.accountName(),
                account == null ? null : account.accountNo(),
                response.paymentDate(),
                response.payType(),
                response.amount(),
                response.status(),
                response.deletedFlag(),
                response.operatorName(),
                response.remark(),
                allocationResponseAssembler.toResponses(entity)
        );
    }

    private PaymentResponse withAccount(PaymentResponse response, SettlementAccountSnapshot account) {
        return account == null ? response : response.withAccount(account.accountName(), account.accountNo());
    }
}
