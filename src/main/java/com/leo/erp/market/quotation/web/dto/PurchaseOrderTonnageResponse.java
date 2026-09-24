package com.leo.erp.market.quotation.web.dto;

import java.math.BigDecimal;

/**
 * 采购订单明细行已开吨位汇总(供比价报价单按规格关联并展示"已开/剩余")。
 *
 * @param purchaseOrderId     采购订单标识
 * @param purchaseOrderItemId 采购订单明细行标识
 * @param orderNo             订单号
 * @param supplierName        供应商
 * @param category            类别
 * @param material            材质
 * @param spec                规格(订单明细为字符串)
 * @param length              长度
 * @param orderedWeight       该明细行订货吨数
 * @param issuedWeight        已开吨位(全部未删除报价单中关联该明细行的 ton 之和)
 * @param remainingWeight     剩余可开吨 = 订货吨数 - 已开吨位
 * @param status              采购订单状态
 */
public record PurchaseOrderTonnageResponse(
        Long purchaseOrderId,
        Long purchaseOrderItemId,
        String orderNo,
        String supplierName,
        String category,
        String material,
        String spec,
        String length,
        BigDecimal orderedWeight,
        BigDecimal issuedWeight,
        BigDecimal remainingWeight,
        String status
) {
}
