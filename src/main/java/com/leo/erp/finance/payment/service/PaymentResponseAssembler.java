package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
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

    public PaymentResponse toSummaryResponse(Payment entity) {
        return paymentMapper.toResponse(entity);
    }

    public PaymentResponse toDetailResponse(Payment entity) {
        PaymentResponse response = paymentMapper.toResponse(entity);
        return new PaymentResponse(
                response.id(),
                response.paymentNo(),
                response.businessType(),
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
}
