package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.api.PurchaseInboundPostedQuery;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * {@link PurchaseInboundPostedQuery} 实现：复用仓储的 keyset 分页与明细批量初始化。
 *
 * <p>与库存期初回填改造前的读取步骤逐字一致：先按 (inboundDate, id) 取一页单据头，
 * 再按 id 批量初始化 items（避免逐单懒加载），最后在同一只读事务内映射为快照。
 * 不 join fetch 明细，保持 LIMIT 下推数据库的原有性能特征。</p>
 */
@Service
public class PurchaseInboundPostedQueryImpl implements PurchaseInboundPostedQuery {

    private final PurchaseInboundRepository purchaseInboundRepository;

    public PurchaseInboundPostedQueryImpl(PurchaseInboundRepository purchaseInboundRepository) {
        this.purchaseInboundRepository = purchaseInboundRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostedInboundSnapshot> findPostedAfter(Collection<String> statuses,
                                                       LocalDate afterDate,
                                                       long afterId,
                                                       int limit) {
        List<PurchaseInbound> page = purchaseInboundRepository.findPostedAfter(
                statuses, afterDate, afterId, PageRequest.of(0, limit));
        if (page.isEmpty()) {
            return List.of();
        }
        purchaseInboundRepository.findAllByIdIn(page.stream().map(PurchaseInbound::getId).toList());
        return page.stream().map(this::toSnapshot).toList();
    }

    private PostedInboundSnapshot toSnapshot(PurchaseInbound inbound) {
        List<LineSnapshot> lines = inbound.getItems().stream()
                .filter(Objects::nonNull)
                .map(PurchaseInboundPostedQueryImpl::toLine)
                .toList();
        return new PostedInboundSnapshot(inbound.getId(), inbound.getInboundNo(), inbound.getInboundDate(),
                inbound.getWarehouseId(), inbound.getWarehouseName(), lines);
    }

    private static LineSnapshot toLine(PurchaseInboundItem item) {
        return new LineSnapshot(item.getId(), item.getMaterialId(), item.getMaterialCode(),
                item.getWarehouseId(), item.getWarehouseName(), item.getBatchNo(),
                item.getQuantity(), item.getQuantityUnit(), item.getUnitPrice());
    }
}
