package com.leo.erp.market.pricelist.service;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 比价单现货价的<b>读时自动推导</b>。
 *
 * <p>口径(契约 4.5 R1.2): 单据品牌列 {@code brandName} → 该品牌在
 * {@code quoteAsOf}(单据报价时刻)之前 {@code released_at} 最大的未删除生效版本 →
 * 命中条目 {@code (category, material, spec, length)} 的 {@code price}。</p>
 *
 * <p>必须用 {@code quoteAsOf} 而不是当前时间, 否则历史单据的现货价会随新版本发布漂移。</p>
 *
 * <p>本类只读; {@code mk_quote_item_price} 的手填覆盖由调用方传入并优先于推导值。</p>
 */
@Service
public class QuoteSheetPriceDeriver {

    private final SupplierPriceListQueryService queryService;

    public QuoteSheetPriceDeriver(SupplierPriceListQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 选中每个品牌的生效版本并加载其条目。
     *
     * @param brandNames  单据品牌列集合
     * @param supplierIds 供应商白名单, 空表示不限
     * @param quoteAsOf   报价时刻
     */
    public BrandSelection selectBrands(Collection<String> brandNames, Collection<Long> supplierIds,
                                       java.time.LocalDateTime quoteAsOf) {
        List<SupplierPriceList> candidates = queryService.activeAsOf(quoteAsOf);
        Map<Long, SupplierPriceList> listById = new LinkedHashMap<>();
        for (SupplierPriceList candidate : candidates) {
            listById.put(candidate.getId(), candidate);
        }
        Map<Long, List<SupplierPriceItem>> itemsByList = queryService.loadItemsByList(candidates);

        Map<String, BrandEntry> byBrand = new LinkedHashMap<>();
        for (String brandName : brandNames) {
            if (brandName == null || brandName.isBlank()) {
                continue;
            }
            String normalized = brandName.trim();
            if (byBrand.containsKey(normalized)) {
                continue;
            }
            SupplierPriceList selected = null;
            for (SupplierPriceList candidate : candidates) {
                if (!normalized.equals(candidate.getBrandName())) {
                    continue;
                }
                if (supplierIds != null && !supplierIds.isEmpty()
                        && !supplierIds.contains(candidate.getSupplierId())) {
                    continue;
                }
                selected = candidate;
                break;
            }
            byBrand.put(normalized, buildEntry(selected, itemsByList));
        }
        return new BrandSelection(byBrand);
    }

    private static BrandEntry buildEntry(SupplierPriceList selected,
                                         Map<Long, List<SupplierPriceItem>> itemsByList) {
        if (selected == null) {
            return new BrandEntry(null, Map.of());
        }
        Map<String, SupplierPriceItem> byKey = new HashMap<>();
        for (SupplierPriceItem item : itemsByList.getOrDefault(selected.getId(), List.of())) {
            byKey.putIfAbsent(item.keyOf(), item);
        }
        return new BrandEntry(selected, byKey);
    }

    /** 按选中结果推导单个格子的现货价。 */
    public static DerivedSpot derive(BrandEntry entry, String category, String material,
                                     Integer spec, String length) {
        if (entry == null || entry.list() == null) {
            return DerivedSpot.unmatched(SpotReason.NO_LIST_AT_TIME);
        }
        SupplierPriceItem item = entry.items().get(SupplierPriceItem.key(category, material, spec, length));
        if (item == null) {
            return DerivedSpot.unmatched(SpotReason.NO_ITEM);
        }
        if (item.getPrice() == null) {
            return DerivedSpot.unmatched(SpotReason.NO_PRICE);
        }
        SupplierPriceList list = entry.list();
        return new DerivedSpot(list.getId(), list.getSupplierId(), list.getSupplierName(),
                list.getReleasedAt(), item.getPrice(), item.getPriceStatus() == null
                        ? null : item.getPriceStatus().name(), null);
    }

    /** 单个格子的推导结果(未命中时 {@code reason} 非空)。 */
    public record DerivedSpot(Long priceListId, Long supplierId, String supplierName,
                              java.time.LocalDateTime priceListReleasedAt, BigDecimal price,
                              String priceStatus, SpotReason reason) {

        static DerivedSpot unmatched(SpotReason reason) {
            return new DerivedSpot(null, null, null, null, null, null, reason);
        }

        public boolean matched() {
            return reason == null && price != null;
        }
    }

    /** 某品牌的选中版本与其条目索引。 */
    public record BrandEntry(SupplierPriceList list, Map<String, SupplierPriceItem> items) {
    }

    /** 全部品牌的选中结果。 */
    public record BrandSelection(Map<String, BrandEntry> byBrand) {

        public BrandEntry entryOf(String brandName) {
            return brandName == null ? null : byBrand.get(brandName.trim());
        }
    }
}
