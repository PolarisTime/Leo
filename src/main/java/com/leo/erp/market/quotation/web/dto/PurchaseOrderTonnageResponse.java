package com.leo.erp.market.quotation.web.dto;

import java.math.BigDecimal;

/**
 * 采购订单已开吨位汇总(供比价报价单吨位列展示"已开/剩余")。
 *
 * @param purchaseOrderId 采购订单标识
 * @param orderNo         订单号
 * @param supplierName    供应商
 * @param orderedWeight   订货吨数(采购订单 total_weight)
 * @param issuedWeight    已开吨位(全部未删除报价单中关联该订单的 ton 之和)
 * @param remainingWeight 剩余可开吨 = 订货吨数 - 已开吨位
 * @param status          采购订单状态
 */
public record PurchaseOrderTonnageResponse(
        Long purchaseOrderId,
        String orderNo,
        String supplierName,
        BigDecimal orderedWeight,
        BigDecimal issuedWeight,
        BigDecimal remainingWeight,
        String status
) {
}
