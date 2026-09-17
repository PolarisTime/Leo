package com.leo.erp.sales.contract.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 销售合同响应; 雪花 ID 由全局 Jackson 配置输出为十进制字符串。 */
public record SalesContractResponse(
        Long id,
        String contractNo,
        String name,
        Long customerId,
        String customerName,
        Long projectId,
        String projectName,
        LocalDate signDate,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalAmount,
        BigDecimal totalTonnage,
        String status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
}
