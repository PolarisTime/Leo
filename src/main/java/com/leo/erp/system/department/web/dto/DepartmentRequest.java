package com.leo.erp.system.department.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank(message = "部门编码不能为空") @Size(max = 64) String departmentCode,
        @NotBlank(message = "部门名称不能为空") @Size(max = 128) String departmentName,
        Long parentId,
        @Size(max = 64) String managerName,
        @Size(max = 32) String contactPhone,
        @Min(value = 0, message = "排序号不能小于0") Integer sortOrder,
        @NotBlank(message = "状态不能为空") @Pattern(regexp = "正常|禁用", message = "状态不合法") String status,
        @Size(max = 255) String remark
) {
}
