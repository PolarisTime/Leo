package com.leo.erp.sales.contract.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import com.leo.erp.sales.contract.repository.SalesOrderContractMetricsRepository;
import com.leo.erp.sales.contract.web.dto.SalesContractCheckResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 销售订单合同金额/吨位校验(只读, 不阻断保存)。
 *
 * <p>累计口径: 合同统计该项目下未删除且状态为「已审核 / 已发出 / 归档」的合同(草稿与作废不计入);
 * 已用金额/吨位统计该项目下所有未删除销售订单, 并按 {@code excludeOrderId} 排除当前订单,
 * 再叠加本次提交的 {@code amount}/{@code tonnage}。合同聚合与订单聚合各用一条 {@code sum(...)}
 * 查询完成, 不做逐单查询, 避免 N+1。</p>
 */
@Service
public class SalesOrderContractCheckService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

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
        BigDecimal pendingAmount = normalizeAmount(amount);
        BigDecimal pendingTonnage = normalizeTonnage(tonnage);

        long contractCount = contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES);
        if (contractCount == 0) {
            return zeroResponse("该项目未关联有效销售合同");
        }

        BigDecimal contractAmount = normalizeAmount(contractRepository
                .sumTotalAmountByProjectIdAndStatusIn(projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES));
        BigDecimal contractTonnage = normalizeTonnage(contractRepository
                .sumTotalTonnageByProjectIdAndStatusIn(projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES));
        BigDecimal usedAmount = normalizeAmount(orderMetricsRepository
                .sumTotalAmountByProjectId(projectId, excludeOrderId)).add(pendingAmount);
        BigDecimal usedTonnage = normalizeTonnage(orderMetricsRepository
                .sumTotalWeightByProjectId(projectId, excludeOrderId)).add(pendingTonnage);

        BigDecimal remainingAmount = contractAmount.subtract(usedAmount);
        BigDecimal remainingTonnage = contractTonnage.subtract(usedTonnage);
        BigDecimal exceededAmount = usedAmount.subtract(contractAmount).max(ZERO);
        BigDecimal exceededTonnage = usedTonnage.subtract(contractTonnage).max(ZERO);

        return new SalesContractCheckResponse(
                true,
                contractAmount,
                usedAmount,
                remainingAmount,
                contractTonnage,
                usedTonnage,
                remainingTonnage,
                exceededAmount,
                exceededTonnage,
                buildMessage(exceededAmount, exceededTonnage)
        );
    }

    private SalesContractCheckResponse zeroResponse(String message) {
        return new SalesContractCheckResponse(
                false,
                ZERO.setScale(PrecisionConstants.AMOUNT_SCALE, RoundingMode.HALF_UP),
                ZERO.setScale(PrecisionConstants.AMOUNT_SCALE, RoundingMode.HALF_UP),
                ZERO.setScale(PrecisionConstants.AMOUNT_SCALE, RoundingMode.HALF_UP),
                ZERO.setScale(PrecisionConstants.WEIGHT_SCALE, RoundingMode.HALF_UP),
                ZERO.setScale(PrecisionConstants.WEIGHT_SCALE, RoundingMode.HALF_UP),
                ZERO.setScale(PrecisionConstants.WEIGHT_SCALE, RoundingMode.HALF_UP),
                ZERO.setScale(PrecisionConstants.AMOUNT_SCALE, RoundingMode.HALF_UP),
                ZERO.setScale(PrecisionConstants.WEIGHT_SCALE, RoundingMode.HALF_UP),
                message
        );
    }

    private String buildMessage(BigDecimal exceededAmount, BigDecimal exceededTonnage) {
        boolean amountExceeded = exceededAmount.signum() > 0;
        boolean tonnageExceeded = exceededTonnage.signum() > 0;
        if (amountExceeded && tonnageExceeded) {
            return "已超出合同额度：金额 " + exceededAmount.toPlainString()
                    + " 元、吨位 " + exceededTonnage.toPlainString() + " 吨";
        }
        if (amountExceeded) {
            return "已超出合同金额 " + exceededAmount.toPlainString() + " 元";
        }
        if (tonnageExceeded) {
            return "已超出合同吨位 " + exceededTonnage.toPlainString() + " 吨";
        }
        return "合同额度充足";
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        BigDecimal resolved = value == null ? ZERO : value;
        return resolved.setScale(PrecisionConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeTonnage(BigDecimal value) {
        BigDecimal resolved = value == null ? ZERO : value;
        return resolved.setScale(PrecisionConstants.WEIGHT_SCALE, RoundingMode.HALF_UP);
    }
}
