package com.leo.erp.sales.returns.service;

import com.leo.erp.sales.api.SalesReturnPostedQuery;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * {@link SalesReturnPostedQuery} 实现：复用仓储的 keyset 分页与明细批量初始化。
 *
 * <p>与库存期初回填改造前的读取步骤逐字一致：先按 (returnDate, id) 取一页单据头，
 * 再按 id 批量初始化 items（避免逐单懒加载），最后在同一只读事务内映射为快照。
 * 不 join fetch 明细，保持 LIMIT 下推数据库的原有性能特征。</p>
 */
@Service
public class SalesReturnPostedQueryImpl implements SalesReturnPostedQuery {

    private final SalesReturnRepository salesReturnRepository;

    public SalesReturnPostedQueryImpl(SalesReturnRepository salesReturnRepository) {
        this.salesReturnRepository = salesReturnRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostedReturnSnapshot> findPostedAfter(String status,
                                                      LocalDate afterDate,
                                                      long afterId,
                                                      int limit) {
        List<SalesReturn> page = salesReturnRepository.findPostedAfter(
                status, afterDate, afterId, PageRequest.of(0, limit));
        if (page.isEmpty()) {
            return List.of();
        }
        salesReturnRepository.findAllByIdIn(page.stream().map(SalesReturn::getId).toList());
        return page.stream().map(this::toSnapshot).toList();
    }

    private PostedReturnSnapshot toSnapshot(SalesReturn salesReturn) {
        List<LineSnapshot> lines = salesReturn.getItems().stream()
                .filter(Objects::nonNull)
                .map(SalesReturnPostedQueryImpl::toLine)
                .toList();
        return new PostedReturnSnapshot(salesReturn.getId(), salesReturn.getReturnNo(),
                salesReturn.getReturnDate(), salesReturn.getWarehouseId(), salesReturn.getWarehouseName(), lines);
    }

    private static LineSnapshot toLine(SalesReturnItem item) {
        return new LineSnapshot(item.getId(), item.getMaterialId(), item.getMaterialCode(),
                item.getWarehouseId(), item.getWarehouseName(), item.getBatchNo(),
                item.getQuantity(), item.getQuantityUnit(), item.getUnitPrice());
    }
}
