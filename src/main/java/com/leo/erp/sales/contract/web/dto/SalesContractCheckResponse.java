package com.leo.erp.sales.contract.web.dto;

import java.math.BigDecimal;

/**
 * 销售订单合同额度校验结果(只读, 不阻断保存)。
 *
 * @param hasContract      项目下是否存在未删除且状态为「已审核」的合同
 * @param contractAmount   有效合同总金额
 * @param usedAmount       该项目下未删除销售订单金额合计 + 本次提交金额(排除 excludeOrderId)
 * @param remainingAmount  合同金额 - 已用金额(可为负)
 * @param contractTonnage  有效合同总吨位
 * @param usedTonnage      该项目下未删除销售订单吨位合计 + 本次提交吨位(排除 excludeOrderId)
 * @param remainingTonnage 合同吨位 - 已用吨位(可为负)
 * @param exceededAmount   超出金额(max(已用-合同, 0))
 * @param exceededTonnage  超出吨位(max(已用-合同, 0))
 * @param message          面向用户的提示文案
 */
public record SalesContractCheckResponse(
        boolean hasContract,
        BigDecimal contractAmount,
        BigDecimal usedAmount,
        BigDecimal remainingAmount,
        BigDecimal contractTonnage,
        BigDecimal usedTonnage,
        BigDecimal remainingTonnage,
        BigDecimal exceededAmount,
        BigDecimal exceededTonnage,
        String message
) {
}
