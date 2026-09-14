package com.leo.erp.sales.order.web.dto;

/**
 * 单据流关系。ID 统一为十进制字符串，避免雪花 ID 在前端丢失精度。
 */
public record SalesOrderDocumentFlowLink(
        String fromType,
        String fromId,
        String toType,
        String toId,
        String linkType
) {
}
