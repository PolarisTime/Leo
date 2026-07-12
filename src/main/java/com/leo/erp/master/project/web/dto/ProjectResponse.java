package com.leo.erp.master.project.web.dto;

public record ProjectResponse(
        Long id,
        String projectCode,
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        String projectManager,
        Long customerId,
        String customerCode,
        String status,
        String remark
) {
    public ProjectResponse(Long id,
                           String projectCode,
                           String projectName,
                           String projectNameAbbr,
                           String projectAddress,
                           String projectManager,
                           String customerCode,
                           String status,
                           String remark) {
        this(id, projectCode, projectName, projectNameAbbr, projectAddress, projectManager,
                null, customerCode, status, remark);
    }
}
