package com.leo.erp.search.flow.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单据流节点：一张参与流转的业务单据。
 * <p>id 为雪花 ID 的十进制字符串，避免前端 JavaScript 精度丢失。</p>
 */
public record DocumentFlowNode(
        String type,
        String id,
        String no,
        String status,
        BigDecimal amount,
        BigDecimal weight,
        LocalDate date
) {
}
