package com.leo.erp.system.department.web.dto;

public record DepartmentResponse(
        Long id,
        String departmentCode,
        String departmentName,
        Long parentId,
        String parentName,
        String managerName,
        String contactPhone,
        Integer sortOrder,
        String status,
        String remark
) {
}
