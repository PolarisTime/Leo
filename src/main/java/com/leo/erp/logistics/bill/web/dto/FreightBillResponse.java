package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightBillResponse(
        Long id,
        String billNo,
        Long carrierId,
        String carrierCode,
        String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long vehicleId,
        String vehiclePlate,
        String customerName,
        String projectName,
        LocalDate billTime,
        BigDecimal unitPrice,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        String status,
        boolean deletedFlag,
        String remark,
        List<FreightBillItemResponse> items
) {
}
