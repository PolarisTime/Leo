package com.leo.erp.master.material.web.dto;

public record MaterialCategoryRequest(
        String categoryCode,
        String categoryName,
        Integer sortOrder,
        String status,
        String remark
) {
}
