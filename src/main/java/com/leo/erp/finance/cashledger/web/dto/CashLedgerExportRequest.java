package com.leo.erp.finance.cashledger.web.dto;

import com.leo.erp.common.validation.SnowflakeId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 资金流水导出请求体。
 *
 * <p>字段语义与 {@code GET /cash-ledger} 的筛选参数完全一致，只是改为 POST 请求体：
 * 导出被建模为资源创建，筛选条件属于资源属性。</p>
 *
 * @param settlementCompanyId 结算主体雪花 id（必填，十进制字符串）
 * @param startDate           开始日期（ISO-8601，可选）
 * @param endDate             结束日期（ISO-8601，可选；不得早于开始日期）
 * @param counterpartyType    往来类型：客户/供应商/物流商（可选）
 * @param counterpartyId      往来单位雪花 id（可选，指定时必须同时指定往来类型）
 * @param flowType            流水类型：RECEIPT/PAYMENT/PAYMENT_REVERSAL/RECEIPT_REVERSAL（可选）
 * @param keyword             关键字（可选）
 */
public record CashLedgerExportRequest(
        @NotNull(message = "结算主体ID不能为空")
        @SnowflakeId
        Long settlementCompanyId,

        LocalDate startDate,

        LocalDate endDate,

        @Size(max = 32, message = "往来类型长度不能超过 32")
        String counterpartyType,

        @SnowflakeId
        Long counterpartyId,

        @Size(max = 32, message = "流水类型长度不能超过 32")
        String flowType,

        @Size(max = 200, message = "关键字长度不能超过 200")
        String keyword
) {
}
