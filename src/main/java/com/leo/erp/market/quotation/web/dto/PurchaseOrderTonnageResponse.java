package com.leo.erp.market.quotation.web.dto;

import java.math.BigDecimal;

/**
 * 采购订单明细行吨位汇总(供比价报价单按规格关联并展示"已开货/剩余")。
 *
 * <p><b>两个"已开"是不同的量, 不可互相替代</b>:</p>
 * <ul>
 *   <li>{@code issuedWeight} = 其他报价单在该明细行上的<b>报单占用</b>(报单需求量之和),
 *       用于回答"这条订单行的额度还能不能给本单再报" —— 选单的可关联性判定用它;</li>
 *   <li>{@code actualWeightTon} = 该明细行的<b>真实已开货</b>(入库审核回写),
 *       用于回答"这条明细行已经开出去多少货" —— 吨位列的已开/剩余用它。</li>
 * </ul>
 *
 * @param purchaseOrderId     采购订单标识
 * @param purchaseOrderItemId 采购订单明细行标识
 * @param orderNo             订单号
 * @param supplierName        供应商
 * @param category            类别
 * @param material            材质
 * @param spec                规格(订单明细为字符串)
 * @param length              长度
 * @param brand               品牌(采购订单明细自带字段, 供比价展示"已开的品牌")
 * @param orderedWeight       该明细行订货吨数
 * @param issuedWeight        报单占用吨位(全部未删除报价单中关联该明细行的 ton 之和, 不含本单据)
 * @param remainingWeight     剩余可开吨 = 订货吨数 - 报单占用吨位
 * @param actualWeightTon     真实已开货吨位(入库审核回写, 可为 null = 尚未开货)
 * @param issuedDone          人工裁定的「已开完」标记
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
        String brand,
        BigDecimal orderedWeight,
        BigDecimal issuedWeight,
        BigDecimal remainingWeight,
        BigDecimal actualWeightTon,
        boolean issuedDone,
        String status
) {
}
