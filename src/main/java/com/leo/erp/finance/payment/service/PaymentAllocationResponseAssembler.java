package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PaymentAllocationResponseAssembler {

    private final SupplierStatementQueryService supplierStatementQueryService;
    private final FreightStatementQueryService freightStatementQueryService;

    public PaymentAllocationResponseAssembler(SupplierStatementQueryService supplierStatementQueryService,
                                              FreightStatementQueryService freightStatementQueryService) {
        this.supplierStatementQueryService = supplierStatementQueryService;
        this.freightStatementQueryService = freightStatementQueryService;
    }

    List<PaymentAllocationResponse> toResponses(Payment entity) {
        return entity.getItems().stream()
                .map(item -> toResponse(entity.getBusinessType(), item))
                .toList();
    }

    private PaymentAllocationResponse toResponse(String businessType, PaymentAllocation item) {
        if (PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(businessType)) {
            SupplierStatement statement = findSupplierStatement(item.getSourceStatementId());
            return new PaymentAllocationResponse(
                    item.getId(),
                    item.getLineNo(),
                    item.getSourceStatementId(),
                    statement == null ? null : statement.getStatementNo(),
                    statement == null ? BigDecimal.ZERO : statement.getClosingAmount(),
                    item.getAllocatedAmount()
            );
        }
        FreightStatement statement = findFreightStatement(item.getSourceStatementId());
        return new PaymentAllocationResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceStatementId(),
                statement == null ? null : statement.getStatementNo(),
                statement == null ? BigDecimal.ZERO : statement.getUnpaidAmount(),
                item.getAllocatedAmount()
        );
    }

    private SupplierStatement findSupplierStatement(Long statementId) {
        return statementId == null ? null : supplierStatementQueryService.findActiveById(statementId).orElse(null);
    }

    private FreightStatement findFreightStatement(Long statementId) {
        return statementId == null ? null : freightStatementQueryService.findActiveById(statementId).orElse(null);
    }
}
