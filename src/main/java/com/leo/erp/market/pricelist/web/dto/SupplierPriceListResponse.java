package com.leo.erp.market.pricelist.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 供应商价格表版本响应。 */
public record SupplierPriceListResponse(
        Long id,
        Long supplierId,
        String supplierName,
        String brandName,
        LocalDateTime releasedAt,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        String warehouse,
        String remark,
        Integer itemCount,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /**
         * 创建新版本时被自动归档的旧版本ID(同供应商+品牌、released_at 更早的生效版本);
         * 无自动归档时为 null。仅创建接口会返回非空。
         */
        Long archivedListId,
        List<ItemResponse> items
) {

    /** 列表摘要(不含 items)。 */
    public record SummaryResponse(
            Long id,
            Long supplierId,
            String supplierName,
            String brandName,
            LocalDateTime releasedAt,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status,
            String warehouse,
            String remark,
            Integer itemCount,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 价格条目。{@code price == null} 表示不报价(与 0 元严格区分)。 */
    public record ItemResponse(
            Long id,
            String category,
            String material,
            Integer spec,
            String length,
            BigDecimal price,
            String priceStatus,
            String remark,
            Integer sortOrder
    ) {
    }
}
