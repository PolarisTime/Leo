package com.leo.erp.master.project.web.dto;

public record ProjectResponse(
        Long id,
        String projectCode,
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        String projectManager,
        String customerCode,
        String status,
        String remark
) {
}
