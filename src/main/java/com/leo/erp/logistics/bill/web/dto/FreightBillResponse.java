package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightBillResponse(
        Long id,
        String billNo,
        String outboundNo,
        String carrierName,
        String vehiclePlate,
        String customerName,
        String projectName,
        LocalDate billTime,
        BigDecimal unitPrice,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        String status,
        String deliveryStatus,
        String remark,
        List<FreightBillItemResponse> items
) {
}
