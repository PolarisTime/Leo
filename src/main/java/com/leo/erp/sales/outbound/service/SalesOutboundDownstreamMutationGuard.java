package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.sales.order.service.SalesOrderDeliveryVerificationGuard;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

@Service
public class SalesOutboundDownstreamMutationGuard {

    private final SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final FreightBillRepository freightBillRepository;

    public SalesOutboundDownstreamMutationGuard(SalesOrderDeliveryVerificationGuard deliveryVerificationGuard,
                                                ReceiptAllocationRepository receiptAllocationRepository,
                                                FreightBillRepository freightBillRepository) {
        this.deliveryVerificationGuard = deliveryVerificationGuard;
        this.receiptAllocationRepository = receiptAllocationRepository;
        this.freightBillRepository = freightBillRepository;
    }

    public void assertReverseAuditAllowed(SalesOutbound outbound) {
        List<Long> sourceSalesOrderItemIds = sourceSalesOrderItemIds(outbound);
        if (!sourceSalesOrderItemIds.isEmpty()) {
            deliveryVerificationGuard.assertMutableByItemIds(sourceSalesOrderItemIds, "反审核");
            if (!receiptAllocationRepository.findReceivedSourceSalesOrderItemIds(
                    sourceSalesOrderItemIds,
                    StatusConstants.RECEIVED
            ).isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库已发生收款，不能反审核");
            }
        }
        List<Long> outboundItemIds = outboundItemIds(outbound);
        if (!outboundItemIds.isEmpty() && !freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(
                outboundItemIds,
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

    private List<Long> outboundItemIds(SalesOutbound outbound) {
        if (outbound == null || outbound.getItems() == null || outbound.getItems().isEmpty()) {
            return List.of();
        }
        TreeSet<Long> itemIds = new TreeSet<>();
        outbound.getItems().stream()
                .map(SalesOutboundItem::getId)
                .filter(java.util.Objects::nonNull)
                .forEach(itemIds::add);
        return List.copyOf(itemIds);
    }
}
