package com.leo.erp.search.flow.web.dto;

import java.math.BigDecimal;

/**
 * 单据流关系边：由上游单据指向下游单据。
 *
 * <p>引用数量/重量为该下游单据实际引用上游明细的数量聚合（件 / 吨），
 * 仅对「采购订单→采购入库、采购入库→销售订单、销售订单→销售出库、销售订单→物流单」
 * 四条明细级关联链路填充；其余链路（对账单、退货等）为 {@code null}，
 * 前端需按可空字段渲染。</p>
 */
public record DocumentFlowLink(
        String fromType,
        String fromId,
        String toType,
        String toId,
        String linkType,
        BigDecimal quantity,
        BigDecimal weight
) {
}
