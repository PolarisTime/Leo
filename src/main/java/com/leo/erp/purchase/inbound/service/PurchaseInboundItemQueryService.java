package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundItemQueryService {

    private final PurchaseInboundItemRepository repository;

    public PurchaseInboundItemQueryService(PurchaseInboundItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseInboundItem> findAllActiveByIdIn(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllActiveByIdIn(ids);
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
}
