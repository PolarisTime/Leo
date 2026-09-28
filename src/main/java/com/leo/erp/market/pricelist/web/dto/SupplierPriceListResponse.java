package com.leo.erp.market.pricelist.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商价格表响应。
 *
 * <p>已取消版本语义(契约 4.6 修订 R2): {@code releasedAt} / {@code status} / {@code effectiveFrom} /
 * {@code effectiveTo} 仅为兼容保留(不参与取版/筛选, {@code status} 新行恒为 {@code ACTIVE});
 * 展示"更新时间"请用 {@code updatedAt}。</p>
 */
public record SupplierPriceListResponse(
        Long id,
        Long supplierId,
        String supplierName,
        String brandName,
        /** 兼容保留: 不再有版本含义, 展示请用 {@code updatedAt}。 */
        LocalDateTime releasedAt,
        /** 兼容保留(可为空)。 */
        LocalDate effectiveFrom,
        /** 兼容保留(可为空)。 */
        LocalDate effectiveTo,
        /** 兼容保留: 新行恒为 ACTIVE。 */
        String status,
        String warehouse,
        String remark,
        Integer itemCount,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 兼容保留: 取消版本语义后不再自动归档旧版, 恒为 null。 */
        Long archivedListId,
        List<ItemResponse> items
) {

    /** 列表摘要(不含 items)。 */
    public record SummaryResponse(
            Long id,
            Long supplierId,
            String supplierName,
            String brandName,
            /** 兼容保留: 不再有版本含义, 展示请用 {@code updatedAt}。 */
            LocalDateTime releasedAt,
            /** 兼容保留(可为空)。 */
            LocalDate effectiveFrom,
            /** 兼容保留(可为空)。 */
            LocalDate effectiveTo,
            /** 兼容保留: 新行恒为 ACTIVE。 */
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
            /** 兼容保留(数据层字段), 维护页不再展示。 */
            String priceStatus,
            String remark,
            Integer sortOrder
    ) {
    }
}
