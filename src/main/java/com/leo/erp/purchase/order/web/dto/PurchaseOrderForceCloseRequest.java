package com.leo.erp.purchase.order.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 强制结单请求(采购订单子资源 {@code force-closures} 的表示)。
 *
 * <p>强制结单把订单剩余未入库件数一次性作废, 并把单据由「已审核」置为「完成采购」;
 * 原因必填, 用于留痕(谁、何时、为什么把剩余量作废)。</p>
 */
public record PurchaseOrderForceCloseRequest(
        @NotBlank(message = "强制结单原因不能为空")
        @Size(max = 255, message = "强制结单原因不能超过 255 个字符")
        String reason
) {
}
