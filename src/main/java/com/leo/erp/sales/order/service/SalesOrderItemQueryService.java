package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SalesOrderItemQueryService {

    private final SalesOrderItemRepository repository;

    public SalesOrderItemQueryService(SalesOrderItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SalesOrderItem> findActiveByIdIn(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return repository.findActiveByIdIn(itemIds);
    }

    @Transactional(readOnly = true)
    public SalesOrderItem requireActiveById(Long id) {
        return repository.findActiveByIdIn(List.of(id)).stream()
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
