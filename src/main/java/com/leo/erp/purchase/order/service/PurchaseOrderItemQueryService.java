package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class PurchaseOrderItemQueryService {

    private final PurchaseOrderItemRepository repository;

    public PurchaseOrderItemQueryService(PurchaseOrderItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderItem> findActiveByIdIn(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return repository.findActiveByIdIn(itemIds);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderItem requireActiveById(Long id) {
        return findActiveByIdIn(List.of(id)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购订单明细不存在"));
    }
}
