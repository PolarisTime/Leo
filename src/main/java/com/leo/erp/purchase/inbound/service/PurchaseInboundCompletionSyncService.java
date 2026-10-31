package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.api.PurchaseSupplierLedgerLock;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.service.PurchaseOrderDirectSalesCapacityGuard;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购完成度同步：<b>「某采购订单明细是否已收满」只有一处实现</b>（{@link #isFullyReceived}）。
 *
 * <p>口径固定为「累计有效入库件数 vs 订货件数」：有效入库 = 未删除且状态为
 * {@code 已审核 / 完成入库} 的入库明细（草稿不计），SQL 与状态白名单统一由
 * {@link PurchaseInboundItemQueryService#summarizeEffectiveQuantityBySourcePurchaseOrderItemIdsExcludingInbound}
 * 提供。入库单的「完成入库」判定与采购订单的「完成采购」判定都调用本类同一实现，
 * 不得在任一分支重新拼一套 SQL 或比较规则。</p>
 */
@Service
public class PurchaseInboundCompletionSyncService {

    private final PurchaseInboundSourceValidator sourceValidator;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;
    private final PurchaseSupplierLedgerLock supplierLedgerLock;
    private final PurchaseOrderDirectSalesCapacityGuard directSalesCapacityGuard;
    private final PurchaseOrderAuditPublisher purchaseOrderAuditPublisher;

    public PurchaseInboundCompletionSyncService(PurchaseInboundSourceValidator sourceValidator,
                                                PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                                PurchaseSupplierLedgerLock supplierLedgerLock,
                                                PurchaseOrderDirectSalesCapacityGuard directSalesCapacityGuard,
                                                PurchaseOrderAuditPublisher purchaseOrderAuditPublisher) {
        this.sourceValidator = sourceValidator;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.supplierLedgerLock = supplierLedgerLock;
        this.directSalesCapacityGuard = directSalesCapacityGuard;
        this.purchaseOrderAuditPublisher = purchaseOrderAuditPublisher;
    }

    /**
     * 本单保存后是否应置「完成入库」。
     *
     * <p>口径与采购订单「完成采购」完全一致（同一 SQL / 同一有效状态白名单 / 同一相等判定）：
     * 草稿入库单<b>不</b>计入累计，因此「草稿兄弟单 50 + 本单审核 50 / 订货 100」不会让本单
     * 变成完成入库。</p>
     *
     * <p>本单此刻可能尚未 flush（新建）或 DB 中仍是旧明细（编辑），因此按 {@code currentInboundId}
     * 排除本单的 DB 记录，再把<b>内存态</b>本单明细按有效状态计入，避免重复计数。</p>
     */
    boolean shouldCompleteInbound(PurchaseInbound inbound) {
        if (!StatusConstants.AUDITED.equals(inbound.getStatus())) {
            return false;
        }
        List<Long> sourcePurchaseOrderItemIds = sourcePurchaseOrderItemIds(inbound);
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return false;
        }
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap =
                sourceValidator.loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds);
        if (sourcePurchaseOrderItemMap.isEmpty()) {
            return false;
        }
        Map<Long, Integer> expectedQuantityBySourceItemId = new HashMap<>();
        for (Long sourceItemId : sourcePurchaseOrderItemIds) {
            PurchaseOrderItem sourceItem = sourcePurchaseOrderItemMap.get(sourceItemId);
            expectedQuantityBySourceItemId.put(
                    sourceItemId,
                    sourceItem == null || sourceItem.getQuantity() == null ? 0 : sourceItem.getQuantity());
        }
        Map<Long, Integer> receivedQuantityBySourceItemId =
                loadEffectiveReceivedQuantityMap(sourcePurchaseOrderItemIds, inbound.getId());
        currentInboundQuantityMap(inbound).forEach(
                (sourceItemId, quantity) -> receivedQuantityBySourceItemId.merge(
                        sourceItemId, quantity, Integer::sum));
        return isFullyReceived(expectedQuantityBySourceItemId, receivedQuantityBySourceItemId);
    }

    /**
     * 收满判定的<b>唯一实现</b>：逐来源明细比较订货件数与累计有效入库件数。
     *
     * <p>相等判定同时要求 {@code expected >= 1}：订货数量为 0 的来源明细不算「收满」，
     * 否则空单据会被误判为完成。</p>
     */
    private static boolean isFullyReceived(Map<Long, Integer> expectedQuantityBySourceItemId,
                                           Map<Long, Integer> receivedQuantityBySourceItemId) {
        if (expectedQuantityBySourceItemId.isEmpty()) {
            return false;
        }
        return expectedQuantityBySourceItemId.entrySet().stream().allMatch(entry -> {
            int expected = entry.getValue() == null ? 0 : entry.getValue();
            int actual = receivedQuantityBySourceItemId.getOrDefault(entry.getKey(), 0);
            return expected >= 1 && expected == actual;
        });
    }

    /**
     * DB 中的累计有效入库件数（未删除 + {@code 已审核/完成入库}）；草稿不计。
     * SQL 与状态白名单只由 {@link PurchaseInboundItemQueryService} 持有。
     */
    private Map<Long, Integer> loadEffectiveReceivedQuantityMap(List<Long> sourcePurchaseOrderItemIds,
                                                                Long excludedInboundId) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, Integer> receivedQuantityBySourceItemId = new HashMap<>();
        purchaseInboundItemQueryService
                .summarizeEffectiveQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                        sourcePurchaseOrderItemIds, excludedInboundId)
                .forEach((sourceItemId, total) -> receivedQuantityBySourceItemId.put(
                        sourceItemId,
                        total == null ? 0 : Math.toIntExact(total)));
        return receivedQuantityBySourceItemId;
    }

    /** 本单内存态明细按来源订单明细聚合的件数（本单状态为已审核，按有效入库计入）。 */
    private Map<Long, Integer> currentInboundQuantityMap(PurchaseInbound inbound) {
        return inbound.getItems().stream()
                .filter(item -> item.getSourcePurchaseOrderItemId() != null)
                .collect(Collectors.groupingBy(
                        PurchaseInboundItem::getSourcePurchaseOrderItemId,
                        Collectors.summingInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                ));
    }

    void synchronizeSourcePurchaseOrders(PurchaseInbound inbound, boolean allowReopen) {
        List<Long> sourcePurchaseOrderItemIds = sourcePurchaseOrderItemIds(inbound);
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return;
        }
        sourceValidator.loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds).values().stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(order -> order != null)
                .distinct()
                .forEach(order -> synchronizePurchaseOrder(order, allowReopen));
    }

    private List<Long> sourcePurchaseOrderItemIds(PurchaseInbound inbound) {
        return inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private void synchronizePurchaseOrder(PurchaseOrder purchaseOrder, boolean allowReopen) {
        if (!StatusConstants.AUDITED.equals(purchaseOrder.getStatus())
                && !StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
            return;
        }
        // 强制结单单据由人工终结: 剩余量已作废, 入库审核/反审核都不得改写其状态。
        if (purchaseOrder.isForceClosed()) {
            return;
        }
        List<Long> sourceItemIds = purchaseOrder.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (sourceItemIds.isEmpty()) {
            return;
        }
        // 完成采购判定与「本单是否完成入库」共用同一实现与同一有效状态白名单：
        // 未删除且状态为已审核/完成入库的入库明细计入，草稿不参与。
        Map<Long, Integer> receivedQtyByItemId =
                directSalesCapacityGuard.loadReceivedQuantityMap(sourceItemIds);
        Map<Long, Integer> expectedQuantityByItemId = purchaseOrder.getItems().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(
                        PurchaseOrderItem::getId,
                        item -> item.getQuantity() == null ? 0 : item.getQuantity()));

        boolean allFulfilled = isFullyReceived(expectedQuantityByItemId, receivedQtyByItemId);

        if (allFulfilled) {
            directSalesCapacityGuard.assertCovered(sourceItemIds, receivedQtyByItemId);
            if (!StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
                lockSupplierLedger(purchaseOrder);
                purchaseOrder.setStatus(StatusConstants.PURCHASE_COMPLETED);
                publishStatusEvent(
                        purchaseOrder,
                        "PURCHASE_ORDER_COMPLETED",
                        StatusConstants.PURCHASE_COMPLETED,
                        "采购订单状态 已审核 -> 完成采购"
                );
            }
        } else if (allowReopen && StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
            purchaseOrder.setStatus(StatusConstants.AUDITED);
            publishStatusEvent(
                    purchaseOrder,
                    "PURCHASE_ORDER_REOPENED",
                    "退回已审核",
                    "采购订单状态 完成采购 -> 已审核"
            );
        }
    }

    private void publishStatusEvent(PurchaseOrder purchaseOrder,
                                    String eventType,
                                    String actionType,
                                    String remark) {
        purchaseOrderAuditPublisher.publish(purchaseOrder, eventType, actionType, remark);
    }

    private void lockSupplierLedger(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getSettlementCompanyId() == null || purchaseOrder.getSupplierId() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单缺少供应商或结算主体身份，不能完成采购"
            );
        }
        supplierLedgerLock.lock(
                purchaseOrder.getSettlementCompanyId(),
                purchaseOrder.getSupplierId()
        );
    }

}
