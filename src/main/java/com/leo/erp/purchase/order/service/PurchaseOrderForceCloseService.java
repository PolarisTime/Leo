package com.leo.erp.purchase.order.service;

import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.api.PurchaseSupplierLedgerLock;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 采购订单强制结单。
 *
 * <p>背景: 「完成采购」只由采购入库审核在"每行入库件数 = 订货件数"时自动触发。剩余件物理作废
 * (报废或供应商不再供货)时订单会永久停在「已审核」, 并继续作为采购入库来源、继续给报单比价
 * 提供可开吨位。强制结单把剩余未入库件数一次性作废, 人工把订单终结为「完成采购」。</p>
 *
 * <p>口径:</p>
 * <ul>
 *   <li>状态复用既有的 {@link StatusConstants#PURCHASE_COMPLETED}: 下游判定(入库来源、供应商
 *       台账锁定、状态筛选、可用量)零改动; 与自动完成的区别由 {@code force_closed} 等留痕字段
 *       承载, 并对外暴露为响应里的 {@code forceClose};</li>
 *   <li>结单时按自动完成的同一套守卫: 校验直接销售占用不超过已入库量, 并锁定供应商台账;</li>
 *   <li>存在引用该订单的未审核入库草稿时拒绝结单(草稿一旦审核, 完成度会与终态冲突);</li>
 *   <li>撤销结单把订单退回「已审核」并清空留痕; 供应商台账锁为"顺序锁定"一次性闩锁, 与入库
 *       反审核回退一致, 不做解锁。</li>
 * </ul>
 */
@Service
public class PurchaseOrderForceCloseService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderAvailabilityService availabilityService;
    private final PurchaseOrderDirectSalesCapacityGuard directSalesCapacityGuard;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;
    private final PurchaseSupplierLedgerLock supplierLedgerLock;
    private final PurchaseOrderAuditPublisher auditPublisher;
    private final PurchaseOrderQueryService queryService;
    private final AccountQuery accountQuery;

    public PurchaseOrderForceCloseService(PurchaseOrderRepository purchaseOrderRepository,
                                          PurchaseOrderAvailabilityService availabilityService,
                                          PurchaseOrderDirectSalesCapacityGuard directSalesCapacityGuard,
                                          PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                          PurchaseSupplierLedgerLock supplierLedgerLock,
                                          PurchaseOrderAuditPublisher auditPublisher,
                                          PurchaseOrderQueryService queryService,
                                          AccountQuery accountQuery) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.availabilityService = availabilityService;
        this.directSalesCapacityGuard = directSalesCapacityGuard;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.supplierLedgerLock = supplierLedgerLock;
        this.auditPublisher = auditPublisher;
        this.queryService = queryService;
        this.accountQuery = accountQuery;
    }

    /** 强制结单: 剩余未入库件数作废, 订单置为「完成采购」并留痕。 */
    @Transactional
    public PurchaseOrderResponse forceClose(Long id, String reason, SecurityPrincipal principal) {
        PurchaseOrder order = requireEntity(id);
        String trimmedReason = normalizeReason(reason);
        if (order.isForceClosed()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该采购订单已强制结单");
        }
        if (!StatusConstants.AUDITED.equals(order.getStatus())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "仅「已审核」的采购订单可以强制结单，当前状态: " + order.getStatus()
            );
        }
        assertNoPendingInbound(order);
        int remainingQuantity = remainingQuantity(order);
        if (remainingQuantity <= 0) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "该采购订单已无未入库件数，请由采购入库审核自动完成"
            );
        }
        List<Long> sourceItemIds = sourceItemIds(order);
        directSalesCapacityGuard.assertCovered(sourceItemIds);
        lockSupplierLedger(order);
        OperatorDisplay operator = resolveOperator(principal);

        order.setStatus(StatusConstants.PURCHASE_COMPLETED);
        order.setForceClosed(true);
        order.setForceCloseReason(trimmedReason);
        order.setForceCloseRemainingQuantity(remainingQuantity);
        order.setForceClosedBy(operator.operatorId());
        order.setForceClosedName(operator.operatorName());
        order.setForceClosedAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        auditPublisher.publish(
                saved,
                "PURCHASE_ORDER_FORCE_CLOSED",
                "强制结单",
                "采购订单状态 已审核 -> 完成采购(强制结单), 作废未入库 " + remainingQuantity
                        + " 件; 原因: " + trimmedReason
        );
        return queryService.toDetailResponse(saved);
    }

    /** 撤销强制结单: 清空留痕并退回「已审核」, 未入库件数重新可入库。 */
    @Transactional
    public PurchaseOrderResponse cancelForceClose(Long id, SecurityPrincipal principal) {
        PurchaseOrder order = requireEntity(id);
        if (!order.isForceClosed()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该采购订单不是强制结单，无需撤销");
        }
        if (!StatusConstants.PURCHASE_COMPLETED.equals(order.getStatus())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "该采购订单状态已不是「完成采购」，无法撤销强制结单"
            );
        }
        Integer writtenOffQuantity = order.getForceCloseRemainingQuantity();
        String previousReason = order.getForceCloseReason();
        OperatorDisplay operator = resolveOperator(principal);
        order.setStatus(StatusConstants.AUDITED);
        order.setForceClosed(false);
        order.setForceCloseReason(null);
        order.setForceCloseRemainingQuantity(null);
        order.setForceClosedBy(null);
        order.setForceClosedName(null);
        order.setForceClosedAt(null);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        auditPublisher.publish(
                saved,
                "PURCHASE_ORDER_FORCE_CLOSE_CANCELLED",
                "撤销强制结单",
                "采购订单状态 完成采购 -> 已审核(撤销强制结单), 恢复未入库 "
                        + (writtenOffQuantity == null ? 0 : writtenOffQuantity)
                        + " 件; 原结单原因: " + previousReason
                        + "; 操作人: " + operator.operatorName()
        );
        return queryService.toDetailResponse(saved);
    }

    /** 操作人留痕: 显示名优先取账号姓名, 缺失时回落登录名; 无登录主体时为 system/0。 */
    private OperatorDisplay resolveOperator(SecurityPrincipal principal) {
        if (principal == null) {
            return new OperatorDisplay(0L, "system");
        }
        String operatorName = accountQuery == null
                ? principal.username()
                : accountQuery.findById(principal.id())
                        .map(AccountQuery.AccountSnapshot::userName)
                        .filter(name -> name != null && !name.isBlank())
                        .orElse(principal.username());
        if (operatorName == null || operatorName.isBlank()) {
            operatorName = principal.username();
        }
        return new OperatorDisplay(principal.id(), operatorName);
    }

    private record OperatorDisplay(Long operatorId, String operatorName) {
    }

    /** 结单时的未入库件数: 与列表「未入库」列同口径(各行 订货量 − 有效入库量 之和, 非负)。 */
    private int remainingQuantity(PurchaseOrder order) {
        Map<Long, Integer> allocatedQuantityMap = availabilityService.loadInboundAllocatedQuantityMap(order);
        long total = order.getItems().stream()
                .mapToLong(item -> Math.max(availabilityService.remainingQuantity(item, allocatedQuantityMap), 0))
                .sum();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * 拒绝存在未审核入库草稿的订单: 草稿不参与完成度判定, 一旦结单后该草稿审核, 完成度会被
     * 重新计算并与终态冲突。
     */
    private void assertNoPendingInbound(PurchaseOrder order) {
        List<Long> sourceItemIds = sourceItemIds(order);
        if (sourceItemIds.isEmpty()) {
            return;
        }
        List<PurchaseInboundItem> items =
                purchaseInboundItemQueryService.findAllActiveBySourcePurchaseOrderItemIds(sourceItemIds);
        boolean hasDraftInbound = items.stream()
                .map(PurchaseInboundItem::getPurchaseInbound)
                .filter(Objects::nonNull)
                .map(PurchaseInbound::getStatus)
                .anyMatch(StatusConstants.DRAFT::equals);
        if (hasDraftInbound) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "该采购订单存在未审核的采购入库单，请先审核或删除后再强制结单"
            );
        }
    }

    private void lockSupplierLedger(PurchaseOrder order) {
        if (order.getSettlementCompanyId() == null || order.getSupplierId() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单缺少供应商或结算主体身份，不能强制结单"
            );
        }
        supplierLedgerLock.lock(order.getSettlementCompanyId(), order.getSupplierId());
    }

    private static String normalizeReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "强制结单原因不能为空");
        }
        if (trimmed.length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "强制结单原因不能超过 255 个字符");
        }
        return trimmed;
    }

    private static List<Long> sourceItemIds(PurchaseOrder order) {
        return order.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private PurchaseOrder requireEntity(Long id) {
        return purchaseOrderRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购订单不存在"));
    }
}
