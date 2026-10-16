package com.leo.erp.purchase.order.web.dto;

import java.time.LocalDateTime;

/**
 * 采购订单明细行「已开完」手动标记响应(带留痕)。
 *
 * <p>人工裁定标记: 不保留任何自动吨位软提示/超量警告; 取消标记后留痕字段清空。</p>
 *
 * @param purchaseOrderItemId 采购订单明细行标识(雪花 ID, JSON 中为十进制字符串)
 * @param issuedDone          是否已开完
 * @param issuedDoneBy        标记操作人ID(取消后为 null)
 * @param issuedDoneName      标记操作人姓名快照(取消后为 null)
 * @param issuedDoneAt        标记时刻(取消后为 null)
 */
public record PurchaseOrderItemIssuedDoneResponse(
        Long purchaseOrderItemId,
        boolean issuedDone,
        Long issuedDoneBy,
        String issuedDoneName,
        LocalDateTime issuedDoneAt
) {
}
