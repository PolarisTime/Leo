package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.statement.api.FreightStatementApi;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PaymentAllocationResponseAssembler {

    private final FreightStatementApi freightStatementApi;

    public PaymentAllocationResponseAssembler(FreightStatementApi freightStatementApi) {
        this.freightStatementApi = freightStatementApi;
    }

    List<PaymentAllocationResponse> toResponses(Payment entity) {
        return entity.getItems().stream()
                .map(item -> toResponse(entity.getBusinessType(), item))
                .toList();
    }

    private PaymentAllocationResponse toResponse(String businessType, PaymentAllocation item) {
        if (PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(businessType)) {
            return new PaymentAllocationResponse(
                    item.getId(),
                    item.getLineNo(),
                    item.getSourceStatementId(),
                    null,
                    null,
                    BigDecimal.ZERO,
                    item.getAllocatedAmount()
            );
        }
        Long statementId = item.getSourceFreightStatementId() == null
                ? item.getSourceStatementId()
                : item.getSourceFreightStatementId();
        FreightStatementApi.Snapshot statement = findFreightStatement(statementId);
        return new PaymentAllocationResponse(
                item.getId(),
                item.getLineNo(),
                statementId,
                statementId,
                statement == null ? null : statement.statementNo(),
                statement == null ? BigDecimal.ZERO : statement.unpaidAmount(),
                item.getAllocatedAmount()
        );
    }

    private FreightStatementApi.Snapshot findFreightStatement(Long statementId) {
        return statementId == null ? null : freightStatementApi.findActiveById(statementId).orElse(null);
    }
}
