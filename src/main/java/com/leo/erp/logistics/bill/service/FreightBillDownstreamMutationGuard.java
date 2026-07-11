package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FreightBillDownstreamMutationGuard {

    private static final String FREIGHT_PAYMENT_TYPE = "物流商";

    private final FreightStatementRepository freightStatementRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;

    public FreightBillDownstreamMutationGuard(FreightStatementRepository freightStatementRepository,
                                              PaymentAllocationRepository paymentAllocationRepository) {
        this.freightStatementRepository = freightStatementRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
    }

    public void assertReverseAuditAllowed(FreightBill bill) {
        assertMutationAllowed(bill, "反审核");
    }

    public void assertDeleteAllowed(FreightBill bill) {
        assertMutationAllowed(bill, "删除");
    }

    private void assertMutationAllowed(FreightBill bill, String action) {
        String billNo = BusinessDocumentValidator.trimToNull(bill == null ? null : bill.getBillNo());
        if (billNo == null) {
            return;
        }
        List<FreightStatement> statements =
                freightStatementRepository.findAllBySourceNosExcludingCurrentStatement(List.of(billNo), null);
        for (FreightStatement statement : statements) {
            long paidAllocationCount = paymentAllocationRepository
                    .countSettledAllocationsByStatementIdAndBusinessTypeAndStatus(
                            statement.getId(),
                            FREIGHT_PAYMENT_TYPE,
                            StatusConstants.PAID
                    );
            if (paidAllocationCount > 0) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "物流单关联的物流对账单已付款，不能" + action
                );
            }
        }
        if (!statements.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流单已生成物流对账单，不能" + action);
        }
    }
}
