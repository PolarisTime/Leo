package com.leo.erp.market.quotation.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 报价单响应。 */
public record QuoteSheetResponse(
        Long id,
        String sheetNo,
        String name,
        Long projectId,
        String projectName,
        LocalDate orderDate,
        LocalDate refDate,
        String refPeriod,
        BigDecimal lengthPremium,
        boolean locked,
        boolean specQuantityLocked,
        String status,
        String remark,
        List<BrandResponse> brands,
        List<ItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {

    public record BrandResponse(Long id, String brandName, BigDecimal freight, Integer sortOrder) {
    }

    public record ItemPriceResponse(Long id, String brandName, BigDecimal spotPrice,
                                    Long supplierId, String supplierName) {
    }

    public record ItemResponse(Long id, Integer lineNo, String category, String material, Integer spec,
                               String length, BigDecimal ton, List<ItemPriceResponse> prices) {
    }
}
