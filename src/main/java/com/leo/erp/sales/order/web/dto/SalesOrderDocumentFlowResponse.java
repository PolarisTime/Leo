package com.leo.erp.sales.order.web.dto;

import java.util.List;

/**
 * 销售订单单据流：订单 → 销售出库 → 销售退货 → 客户对账单 的只读视图。
 */
public record SalesOrderDocumentFlowResponse(
        String salesOrderId,
        List<SalesOrderDocumentFlowNode> nodes,
        List<SalesOrderDocumentFlowLink> links
) {
}
