package com.leo.erp.sales.api;

import java.math.BigDecimal;

/**
 * 项目级客户对账聚合只读端口。
 *
 * <p>销售侧「项目数据」汇总需要「该项目下未删除客户对账单明细」的金额/吨位合计，
 * 而对账数据属于 {@code statement} 模块。若 {@code sales} 直接依赖 {@code statement}
 * 的仓储/实体，会新增模块双向依赖（ArchUnit 门禁的环边基线只允许存量边），
 * 因此本端口按既有的「接口声明在 {@code sales.api}、实现在 {@code statement}」模式提供，
 * 与 {@link SalesOrderStatementReferenceQuery} 同类。</p>
 *
 * <p>口径固定：只统计 {@code deletedFlag = false} 的客户对账单（蓝字与红字都计入）；
 * 红字对账单明细在落库时金额/吨位即为负数，按项目求和后自然抵消。
 * 因此调用方不得再次取绝对值、也不得按 {@code direction} 翻转符号。</p>
 */
public interface ProjectReconciledMetricsQuery {

    /**
     * 汇总项目下未删除客户对账单明细的金额与吨位。
     *
     * @param projectId 项目 ID；{@code null} 或非正数时直接返回
     *                  {@link ProjectReconciledMetrics#ZERO}，不访问数据库
     * @return 金额（元，2 位小数）与吨位（吨，8 位小数）合计；无数据时返回全 0 快照，
     *         实现不得返回 {@code null}
     */
    ProjectReconciledMetrics findByProjectId(Long projectId);

    /**
     * 客户对账聚合快照。
     *
     * @param amount  未删除对账单明细金额合计（红字为负数，结果可为负）
     * @param tonnage 未删除对账单明细吨位合计（红字为负数，结果可为负）
     */
    record ProjectReconciledMetrics(BigDecimal amount, BigDecimal tonnage) {

        /** 零值快照；实现方无数据时返回本常量，调用方据其视为「尚未对账」。 */
        public static final ProjectReconciledMetrics ZERO =
                new ProjectReconciledMetrics(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
