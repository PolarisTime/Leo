package com.leo.erp.report.io.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IoReportResponse(
        Long id,
        LocalDate businessDate,
        String businessType,
        String sourceNo,
        String materialCode,
        String brand,
        String material,
        String category,
        String spec,
        String length,
        String warehouseName,
        String batchNo,
        Integer inQuantity,
        Integer outQuantity,
        String quantityUnit,
        BigDecimal inWeightTon,
        BigDecimal outWeightTon,
        String unit,
        String remark
) {
}
