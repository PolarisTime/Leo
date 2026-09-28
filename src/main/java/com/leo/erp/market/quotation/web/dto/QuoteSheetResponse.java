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

    /**
     * 行×品牌价格格(读时推导结果)。
     *
     * <p>现货价<b>只</b>来自当前供应商价格表: 手填覆盖已彻底删除,
     * {@code mk_quote_item_price} 的历史落库值不再参与读。</p>
     *
     * <ul>
     *   <li>{@code spotPrice}: 当前价格表价(或定尺加价推算价); 未命中为 null;</li>
     *   <li>{@code derivedSpotPrice}: 同 {@code spotPrice}(保留字段, 兼容前端"展示推导值"语义);</li>
     *   <li>{@code spotSource}: {@code PRICE_LIST}(命中该定尺的绝对单价) /
     *       {@code PRICE_LIST_LENGTH_DERIVED}(价格表缺该定尺, 用另一条定尺价 + 项目定尺加价推算) /
     *       {@code NONE}(未命中); 兼容枚举 {@code MANUAL} 保留但读路径不再产生;</li>
     *   <li>{@code spotReason}: {@code NO_LIST}(无价格表) / {@code NO_ITEM}(有表无条目, 含"缺定尺且
     *       项目未配置加价/加价为 0 不推算") / {@code NO_PRICE}(条目不报价), 命中时为空;</li>
     *   <li>{@code derivedFromLength}/{@code lengthPremiumApplied}: 仅
     *       {@code PRICE_LIST_LENGTH_DERIVED} 时非空 —— 推算基准定尺(如 {@code 9米})与叠加的
     *       项目定尺加价(元/吨), 供前端标出「按定尺加价推算」;</li>
     *   <li>{@code priceSource}: 兼容保留, 恒为 null(不再有落库来源快照);</li>
     *   <li>{@code priceListId}/{@code priceListReleasedAt}: 来源价格表ID与 {@code updated_at}
     *       (已取消版本语义, 雪花 ID 为字符串); 未命中时为 null。</li>
     * </ul>
     */
    public record ItemPriceResponse(Long id, String brandName, BigDecimal spotPrice,
                                    Long supplierId, String supplierName,
                                    BigDecimal derivedSpotPrice, String spotSource, String spotReason,
                                    String priceSource, Long priceListId, LocalDateTime priceListReleasedAt,
                                    BigDecimal freight,
                                    /** 定尺加价推算的基准定尺(仅 PRICE_LIST_LENGTH_DERIVED 时非空)。 */
                                    String derivedFromLength,
                                    /** 本次推算叠加的项目定尺加价(元/吨, 仅推算时非空)。 */
                                    BigDecimal lengthPremiumApplied) {

        /** 兼容旧调用方: 未携带推导与来源字段。 */
        public ItemPriceResponse(Long id, String brandName, BigDecimal spotPrice,
                                 Long supplierId, String supplierName) {
            this(id, brandName, spotPrice, supplierId, supplierName, null, null, null, null, null, null, null,
                    null, null);
        }

        /** 兼容旧调用方: 未携带定尺加价推算来源。 */
        public ItemPriceResponse(Long id, String brandName, BigDecimal spotPrice,
                                 Long supplierId, String supplierName,
                                 BigDecimal derivedSpotPrice, String spotSource, String spotReason,
                                 String priceSource, Long priceListId, LocalDateTime priceListReleasedAt,
                                 BigDecimal freight) {
            this(id, brandName, spotPrice, supplierId, supplierName, derivedSpotPrice, spotSource,
                    spotReason, priceSource, priceListId, priceListReleasedAt, freight, null, null);
        }
    }

    public record ItemResponse(Long id, Integer lineNo, QuoteRowType rowType, String category, String material,
                               Integer spec, String length, String remark, BigDecimal ton, boolean purchased,
                               boolean locked,
                               Long purchaseOrderId, String purchaseOrderNo, Long purchaseOrderItemId,
                               List<ItemPriceResponse> prices) {

        /** 兼容旧调用方: 未显式传行类型与行备注。 */
        public ItemResponse(Long id, Integer lineNo, String category, String material, Integer spec,
                            String length, BigDecimal ton, List<ItemPriceResponse> prices) {
            this(id, lineNo, QuoteRowType.PRODUCT, category, material, spec, length, null, ton, false,
                    false, null, null, null, prices);
        }

        /** 兼容旧调用方: 未携带行备注。 */
        public ItemResponse(Long id, Integer lineNo, QuoteRowType rowType, String category, String material,
                            Integer spec, String length, BigDecimal ton, List<ItemPriceResponse> prices) {
            this(id, lineNo, rowType, category, material, spec, length, null, ton, false, false, null, null, null, prices);
        }

        /** 兼容旧调用方: 未携带已采购标记。 */
        public ItemResponse(Long id, Integer lineNo, QuoteRowType rowType, String category, String material,
                            Integer spec, String length, String remark, BigDecimal ton,
                            List<ItemPriceResponse> prices) {
            this(id, lineNo, rowType, category, material, spec, length, remark, ton, false, false, null, null, null, prices);
        }

        /** 兼容旧调用方: 未携带关联采购订单(含明细行)。 */
        public ItemResponse(Long id, Integer lineNo, QuoteRowType rowType, String category, String material,
                            Integer spec, String length, String remark, BigDecimal ton, boolean purchased,
                            List<ItemPriceResponse> prices) {
            this(id, lineNo, rowType, category, material, spec, length, remark, ton, purchased, false, null, null, null, prices);
        }

        /** 兼容旧调用方: 未携带订单明细行关联。 */
        public ItemResponse(Long id, Integer lineNo, QuoteRowType rowType, String category, String material,
                            Integer spec, String length, String remark, BigDecimal ton, boolean purchased,
                            Long purchaseOrderId, String purchaseOrderNo,
                            List<ItemPriceResponse> prices) {
            this(id, lineNo, rowType, category, material, spec, length, remark, ton, purchased, false,
                    purchaseOrderId, purchaseOrderNo, null, prices);
        }
    }
}
