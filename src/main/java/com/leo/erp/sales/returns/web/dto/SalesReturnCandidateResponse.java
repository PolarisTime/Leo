package com.leo.erp.sales.returns.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record SalesReturnCandidateResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long salesOutboundId,
        String salesOutboundNo,
        String salesOrderNo,
        @JsonSerialize(using = ToStringSerializer.class) Long customerId,
        String customerName,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String projectName,
        @JsonSerialize(using = ToStringSerializer.class) Long warehouseId,
        String warehouseName,
        @JsonSerialize(using = ToStringSerializer.class) Long settlementCompanyId,
        String settlementCompanyName,
        List<SalesReturnCandidateItemResponse> items
) {
}
