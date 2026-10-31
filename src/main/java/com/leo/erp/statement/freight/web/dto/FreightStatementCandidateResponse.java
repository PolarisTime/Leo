package com.leo.erp.statement.freight.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 物流对账单来源候选（物流单）。
 *
 * <p>{@code customerName} / {@code projectName} 是<b>已废弃字段</b>：物流单表
 * {@code lg_freight_bill.customer_name/project_name} 已在 V208 删除，物流单的客户/项目信息
 * 只保存在明细（{@code lg_freight_bill_item}）与结算主体字段上，因此这两个字段恒为 {@code null}。
 * 保留字段仅为避免破坏前端候选列契约（{@code aries} 仍声明该两列），
 * 待前端移除候选列后一并删除。</p>
 */
public record FreightStatementCandidateResponse(
        Long id,
        String billNo,
        String carrierCode,
        String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        String customerName,
        String projectName,
        LocalDate billTime,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        String status,
        Long carrierId
) {
    public FreightStatementCandidateResponse(Long id,
                                             String billNo,
                                             String carrierCode,
                                             String carrierName,
                                             Long settlementCompanyId,
                                             String settlementCompanyName,
                                             String customerName,
                                             String projectName,
                                             LocalDate billTime,
                                             BigDecimal totalWeight,
                                             BigDecimal totalFreight,
                                             String status) {
        this(id, billNo, carrierCode, carrierName, settlementCompanyId, settlementCompanyName, customerName,
                projectName, billTime, totalWeight, totalFreight, status, null);
    }

    public FreightStatementCandidateResponse(Long id,
                                             String billNo,
                                             String carrierName,
                                             String customerName,
                                             String projectName,
                                             LocalDate billTime,
                                             BigDecimal totalWeight,
                                             BigDecimal totalFreight,
                                             String status) {
        this(id, billNo, null, carrierName, null, null, customerName, projectName, billTime,
                totalWeight, totalFreight, status, null);
    }
}
