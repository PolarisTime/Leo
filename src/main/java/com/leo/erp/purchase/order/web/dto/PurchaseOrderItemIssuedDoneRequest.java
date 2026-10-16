package com.leo.erp.purchase.order.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 采购订单明细行「已开完」手动标记请求。
 *
 * <p>{@code issuedDone} 必填: {@code true} = 标记已开完, {@code false} = 取消标记;
 * 缺字段按 422(避免"漏传 = 静默取消")。该端点语义与 {@code DELETE .../issued-done} 等价于
 * {@code issuedDone=false}。</p>
 */
public record PurchaseOrderItemIssuedDoneRequest(
        @NotNull(message = "issuedDone 不能为空") Boolean issuedDone
) {
}
