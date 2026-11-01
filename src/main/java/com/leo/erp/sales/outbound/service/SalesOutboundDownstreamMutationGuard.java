package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.api.SalesReturnReferenceQuery;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderDeliveryVerificationGuard;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

@Service
public class SalesOutboundDownstreamMutationGuard {

    private final SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesReturnReferenceQuery salesReturnReferenceQuery;
    private final SourceAllocationLockService sourceAllocationLockService;

    public SalesOutboundDownstreamMutationGuard(SalesOrderDeliveryVerificationGuard deliveryVerificationGuard,
                                                SalesOrderRepository salesOrderRepository,
                                                SalesReturnReferenceQuery salesReturnReferenceQuery,
                                                SourceAllocationLockService sourceAllocationLockService) {
        this.deliveryVerificationGuard = deliveryVerificationGuard;
        this.salesOrderRepository = salesOrderRepository;
        this.salesReturnReferenceQuery = salesReturnReferenceQuery;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    public void assertReverseAuditAllowed(SalesOutbound outbound) {
        List<Long> sourceSalesOrderItemIds = sourceSalesOrderItemIds(outbound);
        if (!sourceSalesOrderItemIds.isEmpty()) {
            assertSourceOrderNotCompleted(sourceSalesOrderItemIds, "反审核");
            deliveryVerificationGuard.assertMutableByItemIds(sourceSalesOrderItemIds, "反审核");
        }
        // 加锁序：上面已按 rank 40/41 锁住销售订单单据与明细，这里再按 50 → 51 取锁，保持升序
        assertNoSalesReturnReferences(outbound, "反审核");
    }

    public void assertDeleteAllowed(SalesOutbound outbound) {
        List<Long> sourceSalesOrderItemIds = sourceSalesOrderItemIds(outbound);
        if (!sourceSalesOrderItemIds.isEmpty()) {
            assertSourceOrderNotCompleted(sourceSalesOrderItemIds, "删除");
        }
        assertNoSalesReturnReferences(outbound, "删除");
    }

    /**
     * 出库反审核/删除前必须确认没有销售退货单引用本单明细。
     *
     * <p>退货审核会生成红字对账单并记退货入库；出库一旦回到草稿或被删除，退货单就会悬挂在
     * 未审核出库明细上（V204 之后外键也不再拦截），库存与对账口径随之失真。</p>
     *
     * <p>锁：{@link SourceAllocationLockService#lockSalesOutboundItemSources} 按全局 rank
     * so_sales_outbound(50) → so_sales_outbound_item(51) 加锁，与销售退货审核
     * （{@code SalesReturnWorkflowService#lockSourceOutboundItems}）完全一致，避免 AB-BA；
     * 先锁后查，覆盖「退货审核与出库反审核并发」窗口。</p>
     */
    private void assertNoSalesReturnReferences(SalesOutbound outbound, String action) {
        List<Long> outboundItemIds = outboundItemIds(outbound);
        if (outboundItemIds.isEmpty()) {
            return;
        }
        sourceAllocationLockService.lockSalesOutboundItemSources(outboundItemIds);
        if (salesReturnReferenceQuery.hasActiveReturnReferences(outboundItemIds)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "销售出库已存在销售退货单，不能" + action + "，请先处理相关退货单"
            );
        }
    }

    private void assertSourceOrderNotCompleted(List<Long> sourceSalesOrderItemIds, String action) {
        boolean completed = salesOrderRepository.findAllWithItemsBySourceItemIds(sourceSalesOrderItemIds).stream()
                .anyMatch(order -> StatusConstants.SALES_COMPLETED.equals(normalize(order.getStatus())));
        if (completed) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "来源销售订单已完成销售，不能" + action + "销售出库"
            );
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
