package com.leo.erp.master.material.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MaterialCategoryRequest(
        @Size(max = 64, message = "类别编码长度不能超过64个字符")
        String categoryCode,
        @NotBlank(message = "类别名称不能为空")
        @Size(max = 64, message = "类别名称长度不能超过64个字符")
        String categoryName,
        @Min(value = 0, message = "排序值不能小于0")
        @Max(value = 9999, message = "排序值不能超过9999")
        Integer sortOrder,
        Boolean purchaseWeighRequired,
        @Size(max = 16, message = "状态长度不能超过16个字符")
        String status,
        @Size(max = 255, message = "备注长度不能超过255个字符")
        String remark
) {
}
