package com.leo.erp.master.material.web.dto;

public record MaterialCategoryResponse(
        Long id,
        String categoryCode,
        String categoryName,
        Integer sortOrder,
        Boolean purchaseWeighRequired,
        String status,
        String remark
) {
}
