package com.leo.erp.master.customer.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CustomerOptionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String label,
        String value,
        String customerCode,
        String customerName,
        @JsonSerialize(using = ToStringSerializer.class) Long defaultSettlementCompanyId,
        String defaultSettlementCompanyName
) {
}
