package com.leo.erp.statement.freight.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 物流对账单来源候选（物流单）。
 *
 * <p>只暴露「选单」需要的字段。历史上曾有 {@code customerName} / {@code projectName} 两个
 * 恒为 {@code null} 的兼容字段：物流单表 {@code lg_freight_bill.customer_name/project_name}
 * 已在 V208 删除，客户/项目信息只保存在明细与结算主体上。前端从未声明这两列，字段已随
 * 2026-11-02 的兼容清理删除；如需按客户/项目筛选，应走物流单明细或结算主体，
 * 不要恢复该字段。</p>
 */
public record FreightStatementCandidateResponse(
        Long id,
        String billNo,
        String carrierCode,
        String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate billTime,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        String status,
        Long carrierId
) {
}
