package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.logistics.api.FreightBillPaymentReferenceQuery;
import com.leo.erp.logistics.api.FreightBillStatementReferenceQuery;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FreightBillDownstreamMutationGuard {

    private final FreightBillStatementReferenceQuery statementReferenceQuery;
    private final FreightBillPaymentReferenceQuery paymentReferenceQuery;

    public FreightBillDownstreamMutationGuard(FreightBillStatementReferenceQuery statementReferenceQuery,
                                              FreightBillPaymentReferenceQuery paymentReferenceQuery) {
        this.statementReferenceQuery = statementReferenceQuery;
        this.paymentReferenceQuery = paymentReferenceQuery;
    }

    public void assertReverseAuditAllowed(FreightBill bill) {
        assertMutationAllowed(bill, "反审核");
    }

    public void assertDeleteAllowed(FreightBill bill) {
        assertMutationAllowed(bill, "删除");
    }

    private void assertMutationAllowed(FreightBill bill, String action) {
        Long billId = bill == null ? null : bill.getId();
        if (billId == null) {
            return;
        }
        List<Long> statementIds = statementReferenceQuery.findActiveStatementIds(billId);
        if (paymentReferenceQuery.hasSettledPaymentReferences(statementIds)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "物流单关联的物流对账单已付款，不能" + action
            );
        }
        if (!statementIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流单已生成物流对账单，不能" + action);
        }
    }
}
