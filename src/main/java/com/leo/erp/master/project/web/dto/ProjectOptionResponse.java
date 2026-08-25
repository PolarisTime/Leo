package com.leo.erp.master.project.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record ProjectOptionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String label,
        @JsonSerialize(using = ToStringSerializer.class) Long value,
        @JsonSerialize(using = ToStringSerializer.class) Long customerId,
        String customerCode,
        String projectCode,
        String projectName,
        String projectNameAbbr,
        @JsonSerialize(using = ToStringSerializer.class) Long settlementCompanyId,
        String settlementCompanyName
) {

    public ProjectOptionResponse(Long id,
                                 String label,
                                 Long value,
                                 Long customerId,
                                 String customerCode,
                                 String projectCode,
                                 String projectName,
                                 String projectNameAbbr) {
        this(id, label, value, customerId, customerCode, projectCode, projectName,
                projectNameAbbr, null, null);
    }
}
