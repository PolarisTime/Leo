package com.leo.erp.market.quotation.web.dto;

import com.leo.erp.market.quotation.domain.enums.QuoteRowType;

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

    /** 以提交后回读的权威版本覆盖当前版本, 其余字段保持不变。 */
    public QuoteSheetResponse withVersion(Long newVersion) {
        return new QuoteSheetResponse(id, sheetNo, name, projectId, projectName, orderDate, refDate, refPeriod,
                lengthPremium, locked, specQuantityLocked, status, remark,
                brands, items, createdAt, updatedAt, newVersion);
    }

    public record BrandResponse(Long id, String brandName, BigDecimal freight, Integer sortOrder) {
    }

    public record ItemPriceResponse(Long id, String brandName, BigDecimal spotPrice,
                                    Long supplierId, String supplierName) {
    }

    public record ItemResponse(Long id, Integer lineNo, QuoteRowType rowType, String category, String material,
                               Integer spec, String length, BigDecimal ton, List<ItemPriceResponse> prices) {

        /** 兼容旧调用方: 未显式传行类型时按商品行处理。 */
        public ItemResponse(Long id, Integer lineNo, String category, String material, Integer spec,
                            String length, BigDecimal ton, List<ItemPriceResponse> prices) {
            this(id, lineNo, QuoteRowType.PRODUCT, category, material, spec, length, ton, prices);
        }
    }
}
