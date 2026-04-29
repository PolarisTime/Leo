package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundItemQueryService {

    private static final String PARENT_MODULE_KEY = "purchase-inbound";

    private final PurchaseInboundItemRepository repository;
    private final ResourceRecordAccessGuard accessGuard;

    public PurchaseInboundItemQueryService(PurchaseInboundItemRepository repository,
                                            ResourceRecordAccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<PurchaseInboundItem> findAllActiveByIdIn(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<PurchaseInboundItem> items = repository.findAllActiveByIdIn(ids);
        for (PurchaseInboundItem item : items) {
            if (item.getPurchaseInbound() != null) {
                accessGuard.assertCurrentUserCanAccess(PARENT_MODULE_KEY, "read", item.getPurchaseInbound());
            }
        }
        return items;
    }

    @Transactional(readOnly = true)
    public PurchaseInboundItem requireActiveById(Long id) {
        return findAllActiveByIdIn(List.of(id)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购入库明细不存在"));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(
            Collection<Long> sourcePurchaseOrderItemIds) {
        if (sourcePurchaseOrderItemIds == null || sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(sourcePurchaseOrderItemIds)
                .stream()
                .collect(Collectors.toMap(
                        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary::getSourcePurchaseOrderItemId,
                        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary::getTotalQuantity
                ));
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(
            Collection<Long> sourcePurchaseOrderItemIds) {
        if (sourcePurchaseOrderItemIds == null || sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return repository
                .summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(sourcePurchaseOrderItemIds, null)
                .stream()
                .collect(Collectors.toMap(
                        PurchaseInboundItemRepository.PurchaseOrderWeightAdjustmentSummary::getSourcePurchaseOrderItemId,
                        PurchaseInboundItemRepository.PurchaseOrderWeightAdjustmentSummary::getTotalWeightAdjustmentTon
                ));
    }
}
