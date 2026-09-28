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
 * <p>口径(契约 4.6 修订 R2, 覆盖 R1.2): 单据品牌列 {@code brandName} → 该品牌的<b>当前</b>
 * 未删除价格表(一个 (供应商, 品牌) 至多一张) → 命中条目
 * {@code (category, material, spec, length)} 的 {@code price}。</p>
 *
 * <p><b>不再按报价时刻取版</b>: 已取消版本语义, 历史单据的现货价会跟随价格表当前值变化
 * (没有历史快照; 要钉住某单据的价就用手填覆盖)。同一品牌有多个供应商的价格表时,
 * 取 {@code updated_at} 最新的一张(见 {@link SupplierPriceListQueryService#currentByBrandNames})。</p>
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
     * 选中每个品牌的当前价格表并加载其条目。
     *
     * @param brandNames  单据品牌列集合
     * @param supplierIds 供应商白名单, 空表示不限
     */
    public BrandSelection selectBrands(Collection<String> brandNames, Collection<Long> supplierIds) {
        List<SupplierPriceList> candidates = queryService.currentByBrandNames(brandNames);
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
                // 查询已按 updated_at DESC NULLS LAST, id DESC 排序: 第一条即最近更新的一张
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

    /**
     * 按选中结果推导单个格子的现货价。
     *
     * <p><b>匹配优先级</b>(见 {@link CategoryNormalizer}):</p>
     * <ol>
     *   <li>先按 {@code (category, material, spec, length)} <b>精确</b>命中
     *       (两边类别写法一致时, 如同为 {@code 盘螺});</li>
     *   <li>精确未命中时, 再按<b>规范化类别</b>命中(镜像前端 {@code normalizeCategory}:
     *       {@code 直条} ≡ {@code 螺纹钢}, 解决 md_material 用 {@code 直条} 而
     *       mk_quote_item 用 {@code 螺纹钢} 的真实主路径);</li>
     *   <li>仍未命中才是 {@code NO_ITEM}。</li>
     * </ol>
     *
     * <p>未命中原因: 该品牌无价格表 → {@code NO_LIST}; 有表无此条目 → {@code NO_ITEM};
     * 条目 {@code price IS NULL} → {@code NO_PRICE}。</p>
     */
    public static DerivedSpot derive(BrandEntry entry, String category, String material,
                                     Integer spec, String length) {
        if (entry == null || entry.list() == null) {
            return DerivedSpot.unmatched(SpotReason.NO_LIST);
        }
        SupplierPriceItem item = matchItem(entry, category, material, spec,
                MaterialSpecCatalogQuery.normalizeLength(length));
        if (item == null) {
            return DerivedSpot.unmatched(SpotReason.NO_ITEM);
        }
        if (item.getPrice() == null) {
            return DerivedSpot.unmatched(SpotReason.NO_PRICE);
        }
        SupplierPriceList list = entry.list();
        return new DerivedSpot(list.getId(), list.getSupplierId(), list.getSupplierName(),
                list.getUpdatedAt(), item.getPrice(), item.getPriceStatus() == null
                        ? null : item.getPriceStatus().name(), null);
    }

    /**
     * 条目匹配: 精确键优先, 未命中时回退到规范化类别。
     *
     * <p>规范化回退时按 {@code (material, spec, length)} 过滤后再比较规范化类别,
     * 并按键升序取第一条以保证确定性; 这样同一 {@code (material, spec, length)} 下
     * 同时存在 {@code 直条} 与 {@code 盘螺} 两个条目时也不会串味。</p>
     *
     * @return 命中的条目; 无命中返回 null
     */
    private static SupplierPriceItem matchItem(BrandEntry entry, String category, String material,
                                               Integer spec, String length) {
        SupplierPriceItem exact = entry.items().get(SupplierPriceItem.key(category, material, spec, length));
        if (exact != null) {
            return exact;
        }
        String normalizedCategory = CategoryNormalizer.normalize(category);
        if (normalizedCategory == null) {
            return null;
        }
        return entry.items().values().stream()
                .filter(item -> java.util.Objects.equals(item.getMaterial(), material))
                .filter(item -> java.util.Objects.equals(item.getSpec(), spec))
                .filter(item -> java.util.Objects.equals(
                        MaterialSpecCatalogQuery.normalizeLength(item.getLength()), length))
                .filter(item -> normalizedCategory.equals(CategoryNormalizer.normalize(item.getCategory())))
                .min(java.util.Comparator.comparing(SupplierPriceItem::keyOf))
                .orElse(null);
    }

    /**
     * 单个格子的推导结果(未命中时 {@code reason} 非空)。
     *
     * @param priceListUpdatedAt 来源价格表的更新时间(取消版本语义后不再有发布时刻;
     *                           调用方把它填入兼容字段 {@code priceListReleasedAt})
     */
    public record DerivedSpot(Long priceListId, Long supplierId, String supplierName,
                              java.time.LocalDateTime priceListUpdatedAt, BigDecimal price,
                              String priceStatus, SpotReason reason) {

        static DerivedSpot unmatched(SpotReason reason) {
            return new DerivedSpot(null, null, null, null, null, null, reason);
        }

        public boolean matched() {
            return reason == null && price != null;
        }
    }

    /** 某品牌的选中价格表与其条目索引。 */
    public record BrandEntry(SupplierPriceList list, Map<String, SupplierPriceItem> items) {
    }

    /** 全部品牌的选中结果。 */
    public record BrandSelection(Map<String, BrandEntry> byBrand) {

        public BrandEntry entryOf(String brandName) {
            return brandName == null ? null : byBrand.get(brandName.trim());
        }
    }
}
