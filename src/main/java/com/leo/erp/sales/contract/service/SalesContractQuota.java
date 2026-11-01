package com.leo.erp.sales.contract.service;

import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 合同额度投影的唯一计算处。
 *
 * <p>「已用金额/吨位」「剩余额度」「超限额度」「使用率」「面向用户的提示文案」全部在本记录内计算，
 * 销售订单保存前的额度校验（{@link SalesOrderContractCheckService}）与项目数据汇总
 * （{@code com.leo.erp.sales.summary.service.ProjectSalesSummaryService}）都只调用这里，
 * 禁止在调用方复制公式——两个出口一旦各写一份，就会在边界值上给出互相矛盾的数字。</p>
 *
 * <p>金额按 {@code PrecisionConstants.AMOUNT_SCALE}（2 位）、吨位按
 * {@code PrecisionConstants.WEIGHT_SCALE}（8 位）归一，舍入统一 HALF_UP；
 * 使用率为 {@code 已用 / 合同}，保留 4 位小数（HALF_UP），合同值 ≤ 0 或无合同时为 {@code null}。</p>
 *
 * <p>{@link #withoutContract()} 是「项目下没有有效合同」的唯一出口：此时除了
 * {@link #hasContract()} 为 {@code false}、{@link #message()} 为提示文案、使用率为 {@code null} 外，
 * 其余额度字段全为按对应精度归零的 0。既有校验端「无合同时 used=0」的行为由此常量锁定，
 * 调用方不得自行拼装零值响应。</p>
 *
 * @param hasContract      项目下是否存在未删除且状态为「审核 / 签发 / 归档」的合同
 * @param contractAmount   有效合同总金额
 * @param usedAmount       已用金额（项目订单累计 + 本次草稿金额）
 * @param remainingAmount  合同金额 - 已用金额（可为负）
 * @param exceededAmount   超出金额，{@code max(已用 - 合同, 0)}
 * @param contractTonnage  有效合同总吨位
 * @param usedTonnage      已用吨位（项目订单累计 + 本次草稿吨位）
 * @param remainingTonnage 合同吨位 - 已用吨位（可为负）
 * @param exceededTonnage  超出吨位，{@code max(已用 - 合同, 0)}
 * @param amountUsageRate  金额使用率（4 位小数）；无有效合同或合同金额 ≤ 0 时为 {@code null}
 * @param tonnageUsageRate 吨位使用率（4 位小数）；无有效合同或合同吨位 ≤ 0 时为 {@code null}
 * @param message          面向用户的提示文案
 */
public record SalesContractQuota(
        boolean hasContract,
        BigDecimal contractAmount,
        BigDecimal usedAmount,
        BigDecimal remainingAmount,
        BigDecimal exceededAmount,
        BigDecimal contractTonnage,
        BigDecimal usedTonnage,
        BigDecimal remainingTonnage,
        BigDecimal exceededTonnage,
        BigDecimal amountUsageRate,
        BigDecimal tonnageUsageRate,
        String message
) {

    /** 无有效合同时的统一提示文案：校验端与汇总端都从这里取值，不得各写一份字面量。 */
    private static final String NO_CONTRACT_MESSAGE = "该项目未关联有效销售合同";

    private static final String SUFFICIENT_MESSAGE = "合同额度充足";

    /**
     * 无有效合同时的额度投影。
     *
     * <p>除提示文案与空使用率外全部为 0：这是既有「无合同时已用=0」行为的唯一来源，
     * 调用方不得自行构造等价零值。</p>
     */
    public static SalesContractQuota withoutContract() {
        return new SalesContractQuota(
                false,
                TradeItemCalculator.scaleAmount(BigDecimal.ZERO),
                TradeItemCalculator.scaleAmount(BigDecimal.ZERO),
                TradeItemCalculator.scaleAmount(BigDecimal.ZERO),
                TradeItemCalculator.scaleAmount(BigDecimal.ZERO),
                TradeItemCalculator.scaleWeightTon(BigDecimal.ZERO),
                TradeItemCalculator.scaleWeightTon(BigDecimal.ZERO),
                TradeItemCalculator.scaleWeightTon(BigDecimal.ZERO),
                TradeItemCalculator.scaleWeightTon(BigDecimal.ZERO),
                null,
                null,
                NO_CONTRACT_MESSAGE
        );
    }

    /**
     * 按有效合同的额度与已用量计算投影。
     *
     * <p>入参允许为 {@code null}（按 0 处理）；返回值的金额字段固定 2 位小数、吨位字段固定 8 位小数。</p>
     *
     * @param contractAmount  有效合同总金额
     * @param usedAmount      已用金额（订单累计 + 草稿金额）
     * @param contractTonnage 有效合同总吨位
     * @param usedTonnage     已用吨位（订单累计 + 草稿吨位）
     * @return 已归一的合同额度投影
     */
    public static SalesContractQuota evaluate(BigDecimal contractAmount,
                                              BigDecimal usedAmount,
                                              BigDecimal contractTonnage,
                                              BigDecimal usedTonnage) {
        BigDecimal normalizedContractAmount = TradeItemCalculator.scaleAmount(contractAmount);
        BigDecimal normalizedUsedAmount = TradeItemCalculator.scaleAmount(usedAmount);
        BigDecimal normalizedContractTonnage = TradeItemCalculator.scaleWeightTon(contractTonnage);
        BigDecimal normalizedUsedTonnage = TradeItemCalculator.scaleWeightTon(usedTonnage);

        BigDecimal exceededAmount = TradeItemCalculator.scaleAmount(
                normalizedUsedAmount.subtract(normalizedContractAmount).max(BigDecimal.ZERO));
        BigDecimal exceededTonnage = TradeItemCalculator.scaleWeightTon(
                normalizedUsedTonnage.subtract(normalizedContractTonnage).max(BigDecimal.ZERO));

        return new SalesContractQuota(
                true,
                normalizedContractAmount,
                normalizedUsedAmount,
                TradeItemCalculator.scaleAmount(normalizedContractAmount.subtract(normalizedUsedAmount)),
                exceededAmount,
                normalizedContractTonnage,
                normalizedUsedTonnage,
                TradeItemCalculator.scaleWeightTon(normalizedContractTonnage.subtract(normalizedUsedTonnage)),
                exceededTonnage,
                usageRate(normalizedUsedAmount, normalizedContractAmount),
                usageRate(normalizedUsedTonnage, normalizedContractTonnage),
                buildMessage(exceededAmount, exceededTonnage)
        );
    }

    /** 使用率：分母 ≤ 0（无额度）时返回 {@code null}，避免除零与无意义的百分比。 */
    private static BigDecimal usageRate(BigDecimal used, BigDecimal contract) {
        if (contract.signum() <= 0) {
            return null;
        }
        return used.divide(contract, PrecisionConstants.USAGE_RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static String buildMessage(BigDecimal exceededAmount, BigDecimal exceededTonnage) {
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
        return SUFFICIENT_MESSAGE;
    }
}
