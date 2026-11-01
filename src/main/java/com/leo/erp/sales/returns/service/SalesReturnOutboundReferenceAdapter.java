package com.leo.erp.sales.returns.service;

import com.leo.erp.sales.api.SalesReturnReferenceQuery;
import com.leo.erp.sales.returns.repository.SalesReturnItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 销售退货单 → 销售出库明细 的引用反查实现（{@link SalesReturnReferenceQuery}）。
 *
 * <p>只读：软删退货单的明细来源列已在 {@code afterDelete} 解绑（见
 * {@link SalesReturnItemRepository#detachSourceReferencesOfDeletedReturn}），这里再按父单
 * {@code deleted_flag = false} 过滤一次，避免历史残留行被当成活跃引用。</p>
 */
@Service
public class SalesReturnOutboundReferenceAdapter implements SalesReturnReferenceQuery {

    private final SalesReturnItemRepository repository;

    public SalesReturnOutboundReferenceAdapter(SalesReturnItemRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findReferencedSalesOutboundItemIds(Collection<Long> salesOutboundItemIds) {
        List<Long> stableIds = normalizeIds(salesOutboundItemIds);
        if (stableIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(repository.findReferencedSourceSalesOutboundItemIds(stableIds));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveReturnReferences(Collection<Long> salesOutboundItemIds) {
        return !findReferencedSalesOutboundItemIds(salesOutboundItemIds).isEmpty();
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
