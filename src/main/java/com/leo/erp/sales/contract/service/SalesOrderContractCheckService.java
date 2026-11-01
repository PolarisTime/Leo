package com.leo.erp.sales.contract.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import com.leo.erp.sales.contract.repository.SalesOrderContractMetricsRepository;
import com.leo.erp.sales.contract.web.dto.SalesContractCheckResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 销售订单合同金额/吨位校验(只读, 不阻断保存)。
 *
 * <p>累计口径: 合同统计该项目下未删除且状态为「审核 / 签发 / 归档」的合同(草稿与作废不计入);
 * 已用金额/吨位统计该项目下所有未删除销售订单, 并按 {@code excludeOrderId} 排除当前订单,
 * 再叠加本次提交的 {@code amount}/{@code tonnage}。合同聚合与订单聚合各用一条 {@code sum(...)}
 * 查询完成, 不做逐单查询, 避免 N+1。</p>
 *
 * <p>已用/剩余/超限/使用率/提示文案全部委托 {@link SalesContractQuota} 计算: 本类只负责取数
 * 与 DTO 映射。项目数据汇总端 ({@code ProjectSalesSummaryService}) 复用同一个计算处,
 * 因此两端在边界值上不会给出相互矛盾的数字。</p>
 */
@Service
public class SalesOrderContractCheckService {

    private final SalesContractRepository contractRepository;
    private final SalesOrderContractMetricsRepository orderMetricsRepository;

    public SalesOrderContractCheckService(SalesContractRepository contractRepository,
                                          SalesOrderContractMetricsRepository orderMetricsRepository) {
        this.contractRepository = contractRepository;
        this.orderMetricsRepository = orderMetricsRepository;
    }

    @Transactional(readOnly = true)
    public SalesContractCheckResponse check(Long projectId,
                                            BigDecimal amount,
                                            BigDecimal tonnage,
                                            Long excludeOrderId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        BigDecimal pendingAmount = TradeItemCalculator.scaleAmount(amount);
        BigDecimal pendingTonnage = TradeItemCalculator.scaleWeightTon(tonnage);

        long contractCount = contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES);
        if (contractCount == 0) {
            // 既有行为: 无有效合同时已用/剩余/超限全部为 0, 由共享计算处锁定。
            return toResponse(SalesContractQuota.withoutContract());
        }

        BigDecimal contractAmount = TradeItemCalculator.scaleAmount(contractRepository
                .sumTotalAmountByProjectIdAndStatusIn(projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES));
        BigDecimal contractTonnage = TradeItemCalculator.scaleWeightTon(contractRepository
                .sumTotalTonnageByProjectIdAndStatusIn(projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES));
        BigDecimal usedAmount = TradeItemCalculator.scaleAmount(orderMetricsRepository
                .sumTotalAmountByProjectId(projectId, excludeOrderId)).add(pendingAmount);
        BigDecimal usedTonnage = TradeItemCalculator.scaleWeightTon(orderMetricsRepository
                .sumTotalWeightByProjectId(projectId, excludeOrderId)).add(pendingTonnage);

        return toResponse(SalesContractQuota.evaluate(
                contractAmount, usedAmount, contractTonnage, usedTonnage));
    }

    /**
     * 把共享口径投影成校验端 DTO。
     *
     * <p>已用/剩余/超限/文案全部来自 {@link SalesContractQuota}，此处只做字段搬运，
     * 不得在本方法内重算任何额度公式。</p>
     */
    private SalesContractCheckResponse toResponse(SalesContractQuota quota) {
        return new SalesContractCheckResponse(
                quota.hasContract(),
                quota.contractAmount(),
                quota.usedAmount(),
                quota.remainingAmount(),
                quota.contractTonnage(),
                quota.usedTonnage(),
                quota.remainingTonnage(),
                quota.exceededAmount(),
                quota.exceededTonnage(),
                quota.message()
        );
    }
}
