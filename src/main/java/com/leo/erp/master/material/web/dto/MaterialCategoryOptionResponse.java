package com.leo.erp.master.material.web.dto;

import java.math.BigDecimal;

public record MaterialCategoryOptionResponse(
        String value,
        String label,
        Boolean purchaseWeighRequired,
        BigDecimal purchaseWeighOverTolerancePercent,
        BigDecimal purchaseWeighUnderTolerancePercent
) {
}
