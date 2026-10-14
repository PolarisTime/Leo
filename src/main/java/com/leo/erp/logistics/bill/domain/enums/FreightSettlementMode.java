package com.leo.erp.logistics.bill.domain.enums;

/**
 * 物流单结算方式: 决定「本次结算金额」({@code lg_freight_bill.total_freight})怎么来。
 *
 * <p>BY_TONNAGE 是历史默认, 结算额 = 单价 × 吨位; FIXED 按固定运费金额结算, 此时单价与吨位
 * 仍照旧存储与展示(其它模块与运费对账单还在读), 但不再作为结算额。</p>
 *
 * <p>两种模式互斥, 校验(含 FIXED 时金额必填且大于 0)在
 * {@code FreightBillService#applySettlementTerms} 唯一一处完成, 非法组合返回 422。</p>
 */
public enum FreightSettlementMode {

    /** 按「单价 × 吨位」结算(历史默认, 既有数据全部为该模式)。 */
    BY_TONNAGE,

    /** 按固定运费金额结算: 固定运费金额必填且大于 0。 */
    FIXED
}
