package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

@Service
public class SalesOutboundDownstreamMutationGuard {

    private final CustomerStatementRepository customerStatementRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final FreightBillRepository freightBillRepository;

    public SalesOutboundDownstreamMutationGuard(CustomerStatementRepository customerStatementRepository,
                                                ReceiptAllocationRepository receiptAllocationRepository,
                                                FreightBillRepository freightBillRepository) {
        this.customerStatementRepository = customerStatementRepository;
        this.receiptAllocationRepository = receiptAllocationRepository;
        this.freightBillRepository = freightBillRepository;
    }

    public void assertReverseAuditAllowed(SalesOutbound outbound) {
        List<Long> sourceSalesOrderItemIds = sourceSalesOrderItemIds(outbound);
        if (!sourceSalesOrderItemIds.isEmpty()) {
            if (!customerStatementRepository.findSourceSalesOrderItemIds(sourceSalesOrderItemIds).isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库已生成客户对账单，不能反审核");
            }
            if (!receiptAllocationRepository.findReceivedSourceSalesOrderItemIds(
                    sourceSalesOrderItemIds,
                    StatusConstants.RECEIVED
            ).isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库已发生收款，不能反审核");
            }
        }
        String outboundNo = trimToNull(outbound == null ? null : outbound.getOutboundNo());
        if (outboundNo != null && !freightBillRepository.findAllBySourceNosExcludingCurrentBill(
                List.of(outboundNo),
                null
        ).isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库已生成物流单，不能反审核");
        }
    }

    private List<Long> sourceSalesOrderItemIds(SalesOutbound outbound) {
        if (outbound == null || outbound.getItems() == null || outbound.getItems().isEmpty()) {
            return List.of();
        }
        TreeSet<Long> sourceIds = new TreeSet<>();
        outbound.getItems().stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(java.util.Objects::nonNull)
                .forEach(sourceIds::add);
        return List.copyOf(sourceIds);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
