package com.leo.erp.market.supplierprice.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 品牌价格表网格（页面形态：一个品牌一张表，规格做行、供应商做列）。
 *
 * <p>列 = 该品牌下的各个供应商价格表头；行 = 商品规格；{@code prices} 以**列 id 为键**，
 * 前端按下标横向铺开，不需要自己对齐供应商顺序。</p>
 *
 * <p>为什么不返回「每行一个供应商数组」：列是动态增删的，数组下标一旦错位就会把甲的价显示到
 * 乙的列上。用列 id 做键，前端按 {@code columns} 的 id 取值，天然不会错位。</p>
 */
public record SupplierPriceGridResponse(
        String brandName,
        List<Column> columns,
        List<Row> rows) {

    /** 一列 = 一家供应商在这张品牌表里的价格表头。 */
    public record Column(
            Long listId,
            Long supplierId,
            String supplierName,
            LocalDate quotedOn,
            LocalDateTime updatedAt,
            String warehouse) {
    }

    /** 一行 = 一个商品规格；{@code prices} 缺失该列键表示该供应商不报这个规格。 */
    public record Row(
            String category,
            String material,
            Integer spec,
            String length,
            Map<Long, BigDecimal> prices,
            Map<Long, String> priceStatuses,
            Map<Long, String> remarks) {
    }
}
