package com.leo.erp.master.material.web.dto;

import java.math.BigDecimal;

public record MaterialCategoryResponse(
        Long id,
        String categoryCode,
        String categoryName,
        Integer sortOrder,
        Boolean purchaseWeighRequired,
        BigDecimal purchaseWeighOverTolerancePercent,
        BigDecimal purchaseWeighUnderTolerancePercent,
        String status,
        String remark
) {
}
