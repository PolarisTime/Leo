package com.leo.erp.statement.customer.service;

import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.api.ProjectReconciledMetricsQuery;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * {@link ProjectReconciledMetricsQuery} 的对账模块实现。
 *
 * <p>销售侧只能依赖 {@code sales.api} 端口，对账聚合留在 {@code statement} 模块内完成，
 * 这样既不把对账单实体/仓储暴露给 {@code sales}，也不新增 {@code sales ↔ statement} 的模块环边。
 * 聚合本身由仓储的两条 {@code sum(...)} 查询承担，本类只负责入参守卫与精度归一。</p>
 *
 * <p>口径：未删除客户对账单（蓝字 + 红字）明细合计；红字明细金额/吨位落库即为负数，
 * 求和后自然抵消，本类不得按 {@code direction} 再翻符号，也不得取绝对值。</p>
 */
@Service
public class CustomerStatementProjectMetricsAdapter implements ProjectReconciledMetricsQuery {

    private final CustomerStatementRepository repository;

    public CustomerStatementProjectMetricsAdapter(CustomerStatementRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectReconciledMetrics findByProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return ProjectReconciledMetrics.ZERO;
        }
        BigDecimal amount = TradeItemCalculator.scaleAmount(repository.sumItemAmountByProjectId(projectId));
        BigDecimal tonnage = TradeItemCalculator.scaleWeightTon(repository.sumItemWeightTonByProjectId(projectId));
        return new ProjectReconciledMetrics(amount, tonnage);
    }
}
