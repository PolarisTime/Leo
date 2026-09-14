package com.leo.erp.sales.order.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单据流节点。ID 统一为十进制字符串，避免雪花 ID 在前端丢失精度。
 */
public record SalesOrderDocumentFlowNode(
        String type,
        String id,
        String no,
        String status,
        BigDecimal amount,
        BigDecimal weight,
        LocalDate date
) {
}
