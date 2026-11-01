package com.leo.erp.sales.summary.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.json.UsageRateSerializer;

import java.math.BigDecimal;

/**
 * 项目销售数据汇总响应（只读，供销售订单编辑器 / 详情弹层 / 列表行展开区展示）。
 *
 * <p>所有金额为元、2 位小数；所有吨位为吨、8 位小数；使用率为 4 位小数（HALF_UP），
 * 无有效合同或合同值 ≤ 0 时为 {@code null}。{@code projectId} 为雪花 ID，
 * 由既有 {@code JacksonConfig} 的 Long→String 序列化输出十进制字符串，DTO 内保持 {@link Long}。</p>
 *
 * <p>口径说明（与冻结契约一致）：</p>
 * <ul>
 *   <li>{@code orderAmount/orderTonnage}：项目下未删除销售订单合计（排除 {@code excludeOrderId}）+ 本次草稿值；</li>
 *   <li>{@code deliveredAmount/deliveredTonnage}：项目下「已审核」销售出库合计 − 「已审核」销售退货合计；</li>
 *   <li>{@code reconciledAmount/reconciledTonnage}：项目下未删除客户对账单明细合计（红字为负数，自然抵消）；</li>
 *   <li>{@code unreconciledAmount/unreconciledTonnage}：{@code delivered − reconciled}，<b>不 clamp 负值</b>
 *       ——为负表示已对账金额超过送货金额（例如退货已红字冲销但出库反审核），是真实业务信号，
 *       调用方不得据此修正或取绝对值；</li>
 *   <li>{@code remaining/exceeded/usageRate/message}：由合同额度唯一计算处
 *       {@code SalesContractQuota} 生成，与销售订单保存前校验口径完全一致。</li>
 * </ul>
 *
 * @param projectId          项目 ID（雪花 ID，序列化为字符串）
 * @param projectName        项目名称
 * @param orderAmount        项目订单金额合计（含本次草稿金额）
 * @param orderTonnage       项目订单吨位合计（含本次草稿吨位）
 * @param deliveredAmount    已审核出库金额 − 已审核退货金额
 * @param deliveredTonnage   已审核出库吨位 − 已审核退货吨位
 * @param reconciledAmount   未删除客户对账单明细金额合计（可为负）
 * @param reconciledTonnage  未删除客户对账单明细吨位合计（可为负）
 * @param unreconciledAmount 未对账金额 = 送货金额 − 已对账金额（不 clamp，可为负）
 * @param unreconciledTonnage 未对账吨位 = 送货吨位 − 已对账吨位（不 clamp，可为负）
 * @param hasContract        项目下是否存在有效销售合同（审核 / 签发 / 归档）
 * @param contractAmount     有效合同总金额；无合同时为 0
 * @param contractTonnage    有效合同总吨位；无合同时为 0
 * @param remainingAmount    合同金额 − 已用金额（可为负）
 * @param remainingTonnage   合同吨位 − 已用吨位（可为负）
 * @param exceededAmount     超出合同金额，{@code max(已用 − 合同, 0)}
 * @param exceededTonnage    超出合同吨位，{@code max(已用 − 合同, 0)}
 * @param amountUsageRate    金额使用率；无有效合同或合同金额 ≤ 0 时为 {@code null}
 * @param tonnageUsageRate   吨位使用率；无有效合同或合同吨位 ≤ 0 时为 {@code null}
 * @param message            面向用户的额度提示文案
 */
public record ProjectSalesSummaryResponse(
        Long projectId,
        String projectName,
        BigDecimal orderAmount,
        BigDecimal orderTonnage,
        BigDecimal deliveredAmount,
        BigDecimal deliveredTonnage,
        BigDecimal reconciledAmount,
        BigDecimal reconciledTonnage,
        BigDecimal unreconciledAmount,
        BigDecimal unreconciledTonnage,
        boolean hasContract,
        BigDecimal contractAmount,
        BigDecimal contractTonnage,
        BigDecimal remainingAmount,
        BigDecimal remainingTonnage,
        BigDecimal exceededAmount,
        BigDecimal exceededTonnage,
        @JsonSerialize(using = UsageRateSerializer.class) BigDecimal amountUsageRate,
        @JsonSerialize(using = UsageRateSerializer.class) BigDecimal tonnageUsageRate,
        String message
) {
}
