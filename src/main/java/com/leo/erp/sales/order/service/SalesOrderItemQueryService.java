package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SalesOrderItemQueryService {

    private static final String PARENT_MODULE_KEY = "sales-order";

    private final SalesOrderItemRepository repository;
    private final ResourceRecordAccessGuard accessGuard;

    public SalesOrderItemQueryService(SalesOrderItemRepository repository,
                                       ResourceRecordAccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<SalesOrderItem> findActiveByIdIn(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        List<SalesOrderItem> items = repository.findActiveByIdIn(itemIds);
        for (SalesOrderItem item : items) {
            if (item.getSalesOrder() != null) {
                accessGuard.assertCurrentUserCanAccess(PARENT_MODULE_KEY, "read", item.getSalesOrder());
            }
        }
        return items;
    }

    @Transactional(readOnly = true)
    public SalesOrderItem requireActiveById(Long id) {
        return findActiveByIdIn(List.of(id)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售订单明细不存在"));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> summarizeAllocatedQuantityBySourceInboundItemIds(
            Collection<Long> sourceInboundItemIds, Long excludeOrderId) {
        if (sourceInboundItemIds == null || sourceInboundItemIds.isEmpty()) {
            return Map.of();
        }
        return repository.summarizeAllocatedQuantityBySourceInboundItemIds(sourceInboundItemIds, excludeOrderId)
                .stream()
                .collect(Collectors.toMap(
                        SalesOrderItemRepository.SourceInboundAllocationSummary::getSourceInboundItemId,
                        SalesOrderItemRepository.SourceInboundAllocationSummary::getTotalQuantity
                ));
    }
}
