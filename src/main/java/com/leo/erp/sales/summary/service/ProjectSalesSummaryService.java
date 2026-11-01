package com.leo.erp.sales.summary.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.sales.api.ProjectReconciledMetricsQuery;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import com.leo.erp.sales.contract.repository.SalesOrderContractMetricsRepository;
import com.leo.erp.sales.contract.service.SalesContractQuota;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import com.leo.erp.sales.summary.web.dto.ProjectSalesSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 项目销售数据只读汇总服务（销售订单编辑器 / 详情弹层 / 列表行展开区共用一个出口）。
 *
 * <p>每个指标只用一条聚合查询取数，不做逐单查询，避免 N+1：</p>
 * <ul>
 *   <li>订单累计：{@link SalesOrderContractMetricsRepository#sumTotalAmountByProjectId(Long, Long)}
 *       与 {@link SalesOrderContractMetricsRepository#sumTotalWeightByProjectId(Long, Long)}
 *       （未删除订单，按 {@code excludeOrderId} 排除当前订单），再叠加 {@code pendingAmount/pendingTonnage}；</li>
 *   <li>已送货：{@link SalesOutboundRepository#sumTotalAmountByProjectIdAndStatus(Long, String)}
 *       − {@link SalesReturnRepository#sumTotalAmountByProjectIdAndStatus(Long, String)}
 *       （均只统计 {@code status=已审核} 且未删除），吨位同理；</li>
 *   <li>已对账：{@link ProjectReconciledMetricsQuery}（实现在对账模块，避免 sales 反向依赖 statement）；</li>
 *   <li>合同额度：{@link SalesContractQuota}（与销售订单保存前校验共用同一计算处）。</li>
 * </ul>
 *
 * <p>未对账 = 已送货 − 已对账，<b>不 clamp 负值</b>：负值意味着已对账超过送货（如退货红字冲销后出库仍存在），
 * 是真实业务信号，不得静默归零。</p>
 *
 * <p>项目不存在、已软删除或已停用（{@link ProjectQuery#findEnabledById(Long)} 为空）时抛
 * {@link ErrorCode#NOT_FOUND}，由全局异常处理器返回 HTTP 404。注意不能退化为
 * {@link ProjectQuery#findActiveById(Long)}：后者只过滤软删除，会把「已停用」项目当成存在。</p>
 */
@Service
public class ProjectSalesSummaryService {

    private final ProjectQuery projectQuery;
    private final SalesContractRepository contractRepository;
    private final SalesOrderContractMetricsRepository orderMetricsRepository;
    private final SalesOutboundRepository outboundRepository;
    private final SalesReturnRepository returnRepository;
    private final ProjectReconciledMetricsQuery reconciledMetricsQuery;

    public ProjectSalesSummaryService(ProjectQuery projectQuery,
                                      SalesContractRepository contractRepository,
                                      SalesOrderContractMetricsRepository orderMetricsRepository,
                                      SalesOutboundRepository outboundRepository,
                                      SalesReturnRepository returnRepository,
                                      ProjectReconciledMetricsQuery reconciledMetricsQuery) {
        this.projectQuery = projectQuery;
        this.contractRepository = contractRepository;
        this.orderMetricsRepository = orderMetricsRepository;
        this.outboundRepository = outboundRepository;
        this.returnRepository = returnRepository;
        this.reconciledMetricsQuery = reconciledMetricsQuery;
    }

    /**
     * 汇总项目销售数据。
     *
     * @param projectId      项目 ID，必填且必须为正数
     * @param excludeOrderId 需要排除的销售订单 ID（当前编辑中的订单），可为 {@code null}
     * @param pendingAmount  本次草稿金额，可为 {@code null}（按 0 处理）
     * @param pendingTonnage 本次草稿吨位，可为 {@code null}（按 0 处理）
     * @return 项目销售汇总；项目下没有任何单据时金额/吨位全为 0，合同字段按合同情况返回
     * @throws BusinessException {@code projectId} 为空/非正数时 {@link ErrorCode#VALIDATION_ERROR}；
     *                           项目不存在或已停用时 {@link ErrorCode#NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public ProjectSalesSummaryResponse summarize(Long projectId,
                                                 Long excludeOrderId,
                                                 BigDecimal pendingAmount,
                                                 BigDecimal pendingTonnage) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        ProjectQuery.ProjectSnapshot project = projectQuery.findEnabledById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, ValidationMessages.PROJECT_NOT_FOUND));

        BigDecimal orderAmount = TradeItemCalculator.scaleAmount(orderMetricsRepository
                .sumTotalAmountByProjectId(projectId, excludeOrderId))
                .add(TradeItemCalculator.scaleAmount(pendingAmount));
        BigDecimal orderTonnage = TradeItemCalculator.scaleWeightTon(orderMetricsRepository
                .sumTotalWeightByProjectId(projectId, excludeOrderId))
                .add(TradeItemCalculator.scaleWeightTon(pendingTonnage));

        BigDecimal deliveredAmount = TradeItemCalculator.scaleAmount(
                TradeItemCalculator.scaleAmount(outboundRepository
                        .sumTotalAmountByProjectIdAndStatus(projectId, StatusConstants.AUDITED))
                        .subtract(TradeItemCalculator.scaleAmount(returnRepository
                                .sumTotalAmountByProjectIdAndStatus(projectId, StatusConstants.AUDITED))));
        BigDecimal deliveredTonnage = TradeItemCalculator.scaleWeightTon(
                TradeItemCalculator.scaleWeightTon(outboundRepository
                        .sumTotalWeightByProjectIdAndStatus(projectId, StatusConstants.AUDITED))
                        .subtract(TradeItemCalculator.scaleWeightTon(returnRepository
                                .sumTotalWeightByProjectIdAndStatus(projectId, StatusConstants.AUDITED))));

        ProjectReconciledMetricsQuery.ProjectReconciledMetrics reconciled = resolveReconciled(projectId);
        BigDecimal reconciledAmount = TradeItemCalculator.scaleAmount(reconciled.amount());
        BigDecimal reconciledTonnage = TradeItemCalculator.scaleWeightTon(reconciled.tonnage());
        // 不 clamp: 负值代表已对账超过送货, 是真实业务信号(见类 Javadoc)。
        BigDecimal unreconciledAmount = TradeItemCalculator.scaleAmount(deliveredAmount.subtract(reconciledAmount));
        BigDecimal unreconciledTonnage = TradeItemCalculator.scaleWeightTon(
                deliveredTonnage.subtract(reconciledTonnage));

        SalesContractQuota quota = resolveQuota(projectId, orderAmount, orderTonnage);

        return new ProjectSalesSummaryResponse(
                project.id(),
                project.name(),
                orderAmount,
                orderTonnage,
                deliveredAmount,
                deliveredTonnage,
                reconciledAmount,
                reconciledTonnage,
                unreconciledAmount,
                unreconciledTonnage,
                quota.hasContract(),
                quota.contractAmount(),
                quota.contractTonnage(),
                quota.remainingAmount(),
                quota.remainingTonnage(),
                quota.exceededAmount(),
                quota.exceededTonnage(),
                quota.amountUsageRate(),
                quota.tonnageUsageRate(),
                quota.message()
        );
    }

    /** 端口实现不得返回 {@code null}; 防御性兜底为零值快照。 */
    private ProjectReconciledMetricsQuery.ProjectReconciledMetrics resolveReconciled(Long projectId) {
        ProjectReconciledMetricsQuery.ProjectReconciledMetrics metrics =
                reconciledMetricsQuery.findByProjectId(projectId);
        return metrics == null ? ProjectReconciledMetricsQuery.ProjectReconciledMetrics.ZERO : metrics;
    }

    /**
     * 取有效合同额度并委托 {@link SalesContractQuota} 计算投影。
     *
     * <p>已用量直接复用订单累计（含本次草稿），与销售订单保存前校验
     * {@code SalesOrderContractCheckService} 的 used 口径完全一致；无有效合同时返回
     * {@link SalesContractQuota#withoutContract()}，不再查询合同金额/吨位。</p>
     */
    private SalesContractQuota resolveQuota(Long projectId, BigDecimal usedAmount, BigDecimal usedTonnage) {
        long contractCount = contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES);
        if (contractCount == 0) {
            return SalesContractQuota.withoutContract();
        }
        BigDecimal contractAmount = TradeItemCalculator.scaleAmount(contractRepository
                .sumTotalAmountByProjectIdAndStatusIn(projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES));
        BigDecimal contractTonnage = TradeItemCalculator.scaleWeightTon(contractRepository
                .sumTotalTonnageByProjectIdAndStatusIn(projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES));
        return SalesContractQuota.evaluate(contractAmount, usedAmount, contractTonnage, usedTonnage);
    }
}
