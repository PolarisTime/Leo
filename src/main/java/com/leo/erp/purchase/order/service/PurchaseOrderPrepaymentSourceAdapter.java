package com.leo.erp.purchase.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.api.PurchaseOrderPrepaymentSnapshot;
import com.leo.erp.purchase.api.PurchaseOrderPrepaymentSourceQuery;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PurchaseOrderPrepaymentSourceAdapter implements PurchaseOrderPrepaymentSourceQuery {

    private static final Set<String> ALLOWED_SOURCE_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.PURCHASE_COMPLETED
    );

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public PurchaseOrderPrepaymentSourceAdapter(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            SourceAllocationLockService sourceAllocationLockService
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PurchaseOrderPrepaymentSnapshot lockAndRequire(
            Long targetPurchaseOrderId,
            Collection<Long> affectedPurchaseOrderIds
    ) {
        if (targetPurchaseOrderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "来源采购订单不能为空");
        }
        TreeSet<Long> orderIds = new TreeSet<>();
        if (affectedPurchaseOrderIds != null) {
            affectedPurchaseOrderIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .forEach(orderIds::add);
        }
        orderIds.add(targetPurchaseOrderId);
        lockSourceItems(orderIds);

        PurchaseOrder sourceOrder = purchaseOrderRepository
                .findByIdAndDeletedFlagFalse(targetPurchaseOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源采购订单不存在"));
        if (!ALLOWED_SOURCE_STATUSES.contains(sourceOrder.getStatus())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购预付款来源采购订单状态必须为已审核或完成采购"
            );
        }
        return toSnapshot(sourceOrder);
    }

    private void lockSourceItems(Collection<Long> purchaseOrderIds) {
        TreeSet<Long> sourceItemIds = new TreeSet<>();
        for (Long purchaseOrderId : purchaseOrderIds) {
            sourceItemIds.addAll(purchaseOrderItemRepository.findActiveIdsByPurchaseOrderId(purchaseOrderId));
        }
        sourceAllocationLockService.lockTradeItemSources(
                List.copyOf(sourceItemIds),
                List.of(),
                List.of()
        );
    }

    private PurchaseOrderPrepaymentSnapshot toSnapshot(PurchaseOrder sourceOrder) {
        String orderNo = requireText(sourceOrder.getOrderNo(), "来源采购订单号不能为空");
        Long supplierId = sourceOrder.getSupplierId();
        if (supplierId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购订单供应商ID不能为空");
        }
        String supplierCode = requireText(sourceOrder.getSupplierCode(), "来源采购订单供应商编码不能为空");
        String supplierName = requireText(sourceOrder.getSupplierName(), "来源采购订单供应商名称不能为空");
        Long settlementCompanyId = sourceOrder.getSettlementCompanyId();
        if (settlementCompanyId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购订单结算主体不能为空");
        }
        String settlementCompanyName = requireText(
                sourceOrder.getSettlementCompanyName(),
                "来源采购订单结算主体名称不能为空"
        );
        return new PurchaseOrderPrepaymentSnapshot(
                sourceOrder.getId(),
                orderNo,
                supplierId,
                supplierCode,
                supplierName,
                settlementCompanyId,
                settlementCompanyName,
                originalAmount(sourceOrder)
        );
    }

    private BigDecimal originalAmount(PurchaseOrder sourceOrder) {
        if (sourceOrder.getItems() == null) {
            return TradeItemCalculator.scaleAmount(BigDecimal.ZERO);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItem item : sourceOrder.getItems()) {
            BigDecimal theoreticalWeight = TradeItemCalculator.calculateWeightTon(
                    item.getQuantity(),
                    item.getPieceWeightTon()
            );
            total = total.add(TradeItemCalculator.calculateAmount(theoreticalWeight, item.getUnitPrice()));
        }
        return TradeItemCalculator.scaleAmount(total);
    }

    private String requireText(String value, String message) {
        String normalized = BusinessDocumentValidator.trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
        return normalized;
    }
}
