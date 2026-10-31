package com.leo.erp.sales.outbound.service;

import com.leo.erp.sales.api.SalesOutboundPostedQuery;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * {@link SalesOutboundPostedQuery} 实现：复用仓储的 keyset 分页与明细批量初始化。
 *
 * <p>与库存期初回填改造前的读取步骤逐字一致：先按 (outboundDate, id) 取一页单据头，
 * 再按 id 批量初始化 items（避免逐单懒加载），最后在同一只读事务内映射为快照。
 * 不 join fetch 明细，保持 LIMIT 下推数据库的原有性能特征。</p>
 */
@Service
public class SalesOutboundPostedQueryImpl implements SalesOutboundPostedQuery {

    private final SalesOutboundRepository salesOutboundRepository;

    public SalesOutboundPostedQueryImpl(SalesOutboundRepository salesOutboundRepository) {
        this.salesOutboundRepository = salesOutboundRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostedOutboundSnapshot> findPostedAfter(String status,
                                                        LocalDate afterDate,
                                                        long afterId,
                                                        int limit) {
        List<SalesOutbound> page = salesOutboundRepository.findPostedAfter(
                status, afterDate, afterId, PageRequest.of(0, limit));
        if (page.isEmpty()) {
            return List.of();
        }
        salesOutboundRepository.findAllByIdIn(page.stream().map(SalesOutbound::getId).toList());
        return page.stream().map(this::toSnapshot).toList();
    }

    private PostedOutboundSnapshot toSnapshot(SalesOutbound outbound) {
        List<LineSnapshot> lines = outbound.getItems().stream()
                .filter(Objects::nonNull)
                .map(SalesOutboundPostedQueryImpl::toLine)
                .toList();
        return new PostedOutboundSnapshot(outbound.getId(), outbound.getOutboundNo(), outbound.getOutboundDate(),
                outbound.getWarehouseId(), outbound.getWarehouseName(), lines);
    }

    private static LineSnapshot toLine(SalesOutboundItem item) {
        return new LineSnapshot(item.getId(), item.getMaterialId(), item.getMaterialCode(),
                item.getWarehouseId(), item.getWarehouseName(), item.getBatchNo(),
                item.getQuantity(), item.getQuantityUnit(), item.getUnitPrice());
    }
}
