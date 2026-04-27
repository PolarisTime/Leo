package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class PurchaseOrderItemQueryService {

    private static final String PARENT_MODULE_KEY = "purchase-order";

    private final PurchaseOrderItemRepository repository;
    private final ResourceRecordAccessGuard accessGuard;

    public PurchaseOrderItemQueryService(PurchaseOrderItemRepository repository,
                                          ResourceRecordAccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderItem> findActiveByIdIn(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        List<PurchaseOrderItem> items = repository.findActiveByIdIn(itemIds);
        for (PurchaseOrderItem item : items) {
            if (item.getPurchaseOrder() != null) {
                accessGuard.assertCurrentUserCanAccess(PARENT_MODULE_KEY, "read", item.getPurchaseOrder());
            }
        }
        return items;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderItem requireActiveById(Long id) {
        return findActiveByIdIn(List.of(id)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购订单明细不存在"));
    }
}
