package com.leo.erp.master.project.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

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
        String settlementCompanyName,
        /** 网价浮动方向: ADD加价/SUBTRACT减价; 空表示不浮动。 */
        String priceFloatMode,
        /** 网价固定浮动幅度(元/吨), 非负。 */
        BigDecimal priceFloatValue
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
                projectNameAbbr, null, null, null, null);
    }

    public ProjectOptionResponse(Long id,
                                 String label,
                                 Long value,
                                 Long customerId,
                                 String customerCode,
                                 String projectCode,
                                 String projectName,
                                 String projectNameAbbr,
                                 Long settlementCompanyId,
                                 String settlementCompanyName) {
        this(id, label, value, customerId, customerCode, projectCode, projectName,
                projectNameAbbr, settlementCompanyId, settlementCompanyName, null, null);
    }
}
