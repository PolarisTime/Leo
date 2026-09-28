package com.leo.erp.market.pricelist.service;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    /** 现货价来源: 价格表命中该定尺的绝对单价。 */
    public static final String SOURCE_PRICE_LIST = "PRICE_LIST";

    /** 现货价来源: 价格表缺该定尺, 用另一条定尺的绝对价 + 项目定尺加价推算。 */
    public static final String SOURCE_PRICE_LIST_LENGTH_DERIVED = "PRICE_LIST_LENGTH_DERIVED";

    private final SupplierPriceListQueryService queryService;
    private final ValueAliasQuery valueAliasQuery;

    public QuoteSheetPriceDeriver(SupplierPriceListQueryService queryService,
                                  ValueAliasQuery valueAliasQuery) {
        this.queryService = queryService;
        this.valueAliasQuery = valueAliasQuery;
    }

    /**
     * 选中每个品牌的当前价格表并加载其条目。
     *
     * <p><b>品牌匹配走值映射</b>({@code md_value_alias} 的 BRAND 维度): 单据品牌列与价格表
     * {@code brand_name} 两边都按 BRAND 映射归一后比较 —— 单据用 {@code 富鑫}、价格表存
     * {@code 安徽富鑫} 时, 只要配置了该映射即可命中; 未配置映射时等价于原来的 trim 后精确比较。
     * 查询按"原写法 ∪ 归一后写法"取候选, 因此两种写法存哪边都能找到。</p>
     *
     * @param brandNames  单据品牌列集合
     * @param supplierIds 供应商白名单, 空表示不限
     */
    public BrandSelection selectBrands(Collection<String> brandNames, Collection<Long> supplierIds) {
        ValueAliasQuery.AliasRules rules = valueAliasQuery.rules();
        Map<String, String> targetByBrand = new LinkedHashMap<>();
        Set<String> lookupNames = new LinkedHashSet<>();
        if (brandNames != null) {
            for (String brandName : brandNames) {
                if (brandName == null || brandName.isBlank()) {
                    continue;
                }
                String raw = brandName.trim();
                String target = rules.normalizeBrand(raw);
                targetByBrand.putIfAbsent(raw, target);
                lookupNames.add(raw);
                if (target != null && !target.isBlank()) {
                    lookupNames.add(target);
                }
            }
        }
        List<SupplierPriceList> candidates = lookupNames.isEmpty()
                ? List.of()
                : queryService.currentByBrandNames(lookupNames);
        Map<Long, List<SupplierPriceItem>> itemsByList = queryService.loadItemsByList(candidates);

        Map<String, BrandEntry> byBrand = new LinkedHashMap<>();
        for (Map.Entry<String, String> requested : targetByBrand.entrySet()) {
            String target = requested.getValue();
            SupplierPriceList selected = null;
            for (SupplierPriceList candidate : candidates) {
                if (!Objects.equals(rules.normalizeBrand(candidate.getBrandName()), target)) {
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
            byBrand.put(requested.getKey(), buildEntry(selected, itemsByList, rules));
        }
        return new BrandSelection(byBrand);
    }

    private static BrandEntry buildEntry(SupplierPriceList selected,
                                         Map<Long, List<SupplierPriceItem>> itemsByList,
                                         ValueAliasQuery.AliasRules rules) {
        if (selected == null) {
            return new BrandEntry(null, Map.of(), rules);
        }
        Map<String, SupplierPriceItem> byKey = new HashMap<>();
        for (SupplierPriceItem item : itemsByList.getOrDefault(selected.getId(), List.of())) {
            byKey.putIfAbsent(item.keyOf(), item);
        }
        return new BrandEntry(selected, byKey, rules);
    }

    /**
     * 按选中结果推导单个格子的现货价。
     *
     * <p><b>匹配优先级</b>(归一化单点实现见 {@link ValueAliasQuery}):</p>
     * <ol>
     *   <li>先按 {@code (category, material, spec, length)} <b>精确</b>命中
     *       (两边写法一致时, 如同为 {@code 盘螺});</li>
     *   <li>精确未命中时, 再按<b>值映射归一后</b>的键命中: 类别/材质/定尺三个维度都查
     *       {@code md_value_alias}(类别未配置映射时回退硬编码 {@link CategoryNormalizer} 的
     *       {@code 直条} ≡ {@code 螺纹钢}, 即改造前的行为);</li>
     *   <li>仍未命中才是 {@code NO_ITEM}。</li>
     * </ol>
     *
     * <p>未命中原因: 该品牌无价格表 → {@code NO_LIST}; 有表无此条目 → {@code NO_ITEM};
     * 条目 {@code price IS NULL} → {@code NO_PRICE}。</p>
     *
     * <p>兼容签名: 不带项目定尺加价 → 不做定尺加价推算(缺定尺条目仍是 {@code NO_ITEM})。</p>
     */
    public static DerivedSpot derive(BrandEntry entry, String category, String material,
                                     Integer spec, String length) {
        return derive(entry, category, material, spec, length, null);
    }

    /**
     * 按选中结果推导单个格子的现货价, 含<b>定尺加价推算</b>。
     *
     * <p><b>取价规则(契约 ②)</b>: 定尺加价规则只存在于项目级配置
     * ({@code mk_quote_project_config.length_premium}, 单据侧快照 {@code mk_quote_sheet.length_premium}),
     * 价格表侧不存规则、只存绝对单价:</p>
     * <ol>
     *   <li>该定尺在价格表里有条目且 {@code price IS NOT NULL} → 直接用该<b>绝对单价</b>
     *       ({@code spotSource=PRICE_LIST}), 不加任何加价;</li>
     *   <li>该定尺条目存在但 {@code price IS NULL} = 显式"不报价" → {@code NO_PRICE},
     *       <b>不</b>用其它定尺推算(尊重"不报价"的业务意图);</li>
     *   <li>该 (供应商, 品牌) 的价格表<b>没有该定尺条目</b>时, 用「同类别 + 同材质 + 同规格、
     *       且有绝对价的另一条定尺」的单价 {@code + lengthPremium} 推一个价
     *       ({@code spotSource=PRICE_LIST_LENGTH_DERIVED}, 并回填
     *       {@code derivedFromLength}/{@code lengthPremiumApplied} 供前端标出"按定尺加价推算");
     *       例: 12 米缺价时用 9 米价 + 加价;</li>
     *   <li>{@code lengthPremium} 为空或 {@code <= 0}, 或(由调用方判定)项目<b>未配置</b>时
     *       <b>不推算</b>, 保持 {@code NO_ITEM}。</li>
     * </ol>
     *
     * <p><b>基准定尺的确定性口径</b>: 候选 = 同 (类别, 材质, 规格)、{@code price} 非空、归一后定尺
     * 不同于请求定尺的条目; 取「定尺数值与请求定尺数值的距离」最小者, 距离相同取较短定尺,
     * 再按键升序; 无定尺/非数值定尺(距离视为无穷)排在最后。加价方向恒为
     * {@code 基准价 + lengthPremium}(不做反向减价), 因此只应把该推算理解为"更长定尺缺价时的推价"。</p>
     */
    public static DerivedSpot derive(BrandEntry entry, String category, String material,
                                     Integer spec, String length, BigDecimal lengthPremium) {
        if (entry == null || entry.list() == null) {
            return DerivedSpot.unmatched(SpotReason.NO_LIST);
        }
        SupplierPriceItem item = matchItem(entry, category, material, spec, length);
        if (item != null) {
            if (item.getPrice() == null) {
                return DerivedSpot.unmatched(SpotReason.NO_PRICE);
            }
            return DerivedSpot.of(entry.list(), item, null, null);
        }
        if (lengthPremium == null || lengthPremium.compareTo(BigDecimal.ZERO) <= 0) {
            return DerivedSpot.unmatched(SpotReason.NO_ITEM);
        }
        SupplierPriceItem base = pickLengthBase(entry, category, material, spec, length);
        if (base == null) {
            return DerivedSpot.unmatched(SpotReason.NO_ITEM);
        }
        return DerivedSpot.of(entry.list(), base, base.getLength(), lengthPremium);
    }

    /** 定尺加价推算的基准条目(见 {@link #derive(BrandEntry, String, String, Integer, String, BigDecimal)})。 */
    private static SupplierPriceItem pickLengthBase(BrandEntry entry, String category, String material,
                                                    Integer spec, String length) {
        ValueAliasQuery.AliasRules rules = entry.rules();
        String normalizedCategory = rules.normalizeCategory(category);
        String normalizedMaterial = rules.normalizeMaterial(material);
        if (normalizedMaterial == null) {
            return null;
        }
        String normalizedLength = rules.normalizeLength(length);
        Integer requested = lengthValue(normalizedLength);
        List<SupplierPriceItem> candidates = new ArrayList<>();
        for (SupplierPriceItem item : entry.items().values()) {
            if (item.getPrice() == null || !Objects.equals(item.getSpec(), spec)) {
                continue;
            }
            if (!Objects.equals(rules.normalizeMaterial(item.getMaterial()), normalizedMaterial)) {
                continue;
            }
            if (normalizedCategory == null
                    || !normalizedCategory.equals(rules.normalizeCategory(item.getCategory()))) {
                continue;
            }
            if (Objects.equals(rules.normalizeLength(item.getLength()), normalizedLength)) {
                continue;
            }
            candidates.add(item);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator
                .comparingLong((SupplierPriceItem item) -> distance(
                        lengthValue(rules.normalizeLength(item.getLength())), requested))
                .thenComparing(item -> lengthValue(rules.normalizeLength(item.getLength())),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SupplierPriceItem::keyOf));
        return candidates.get(0);
    }

    /** 定尺数值(如 {@code 12米} → 12); 无定尺或非数值返回 null。 */
    private static Integer lengthValue(String normalizedLength) {
        String digits = MaterialSpecCatalogQuery.lengthSortKey(normalizedLength);
        if (digits.isEmpty() || digits.contains(".")) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 与请求定尺的数值距离; 任一侧无数值时取 {@link Long#MAX_VALUE}(排到最后)。 */
    private static long distance(Integer candidate, Integer requested) {
        if (candidate == null || requested == null) {
            return Long.MAX_VALUE;
        }
        return Math.abs((long) candidate - requested);
    }

    /**
     * 条目匹配: 精确键优先, 未命中时回退到"值映射归一后"的键。
     *
     * <p>归一化回退时按 {@code (材质, 规格, 定尺)} 归一无误后再比较归一化类别,
     * 并按键升序取第一条以保证确定性; 这样同一 {@code (material, spec, length)} 下
     * 同时存在 {@code 直条} 与 {@code 盘螺} 两个条目时也不会串味。</p>
     *
     * @return 命中的条目; 无命中返回 null
     */
    private static SupplierPriceItem matchItem(BrandEntry entry, String category, String material,
                                               Integer spec, String length) {
        // 精确键: 定尺按结构口径归一(与入库口径一致), 类别/材质保持原写法
        String structuralLength = MaterialSpecCatalogQuery.normalizeLength(length);
        SupplierPriceItem exact = entry.items().get(SupplierPriceItem.key(category, material, spec,
                structuralLength));
        if (exact != null) {
            return exact;
        }
        ValueAliasQuery.AliasRules rules = entry.rules();
        String normalizedCategory = rules.normalizeCategory(category);
        if (normalizedCategory == null) {
            return null;
        }
        String normalizedMaterial = rules.normalizeMaterial(material);
        String normalizedLength = rules.normalizeLength(length);
        return entry.items().values().stream()
                .filter(item -> Objects.equals(rules.normalizeMaterial(item.getMaterial()), normalizedMaterial))
                .filter(item -> Objects.equals(item.getSpec(), spec))
                .filter(item -> Objects.equals(rules.normalizeLength(item.getLength()), normalizedLength))
                .filter(item -> normalizedCategory.equals(rules.normalizeCategory(item.getCategory())))
                .min(Comparator.comparing(SupplierPriceItem::keyOf))
                .orElse(null);
    }

    /**
     * 单个格子的推导结果(未命中时 {@code reason} 非空)。
     *
     * @param priceListUpdatedAt 来源价格表的更新时间(取消版本语义后不再有发布时刻;
     *                           调用方把它填入兼容字段 {@code priceListReleasedAt})
     * @param derivedFromLength  定尺加价推算的基准定尺(如 {@code 9米}); 直接命中绝对价时为 null
     * @param lengthPremiumApplied 本次推算叠加的项目定尺加价(元/吨); 直接命中绝对价时为 null
     */
    public record DerivedSpot(Long priceListId, Long supplierId, String supplierName,
                              java.time.LocalDateTime priceListUpdatedAt, BigDecimal price,
                              String priceStatus, SpotReason reason,
                              String derivedFromLength, BigDecimal lengthPremiumApplied) {

        static DerivedSpot unmatched(SpotReason reason) {
            return new DerivedSpot(null, null, null, null, null, null, reason, null, null);
        }

        /** 兼容旧调用方: 未携带定尺加价推算来源。 */
        public DerivedSpot(Long priceListId, Long supplierId, String supplierName,
                           java.time.LocalDateTime priceListUpdatedAt, BigDecimal price,
                           String priceStatus, SpotReason reason) {
            this(priceListId, supplierId, supplierName, priceListUpdatedAt, price, priceStatus, reason,
                    null, null);
        }

        /**
         * 命中条目: {@code lengthPremiumApplied} 非空时按 {@code 基准价 + 加价} 出价并带上推算来源,
         * 为空时直接用条目绝对单价。
         */
        static DerivedSpot of(SupplierPriceList list, SupplierPriceItem item, String derivedFromLength,
                              BigDecimal lengthPremiumApplied) {
            BigDecimal price = lengthPremiumApplied == null
                    ? item.getPrice()
                    : item.getPrice().add(lengthPremiumApplied);
            return new DerivedSpot(list.getId(), list.getSupplierId(), list.getSupplierName(),
                    list.getUpdatedAt(), price, item.getPriceStatus() == null
                            ? null : item.getPriceStatus().name(), null,
                    derivedFromLength, lengthPremiumApplied);
        }

        public boolean matched() {
            return reason == null && price != null;
        }

        /** 响应里的 {@code spotSource}: 绝对价 {@code PRICE_LIST}, 定尺加价推算 {@code PRICE_LIST_LENGTH_DERIVED}。 */
        public String spotSource() {
            return derivedFromLength == null ? SOURCE_PRICE_LIST : SOURCE_PRICE_LIST_LENGTH_DERIVED;
        }
    }

    /**
     * 某品牌的选中价格表与其条目索引, 以及本次推导使用的值映射快照。
     *
     * <p>两参数构造(不带映射)等价于"未配置任何映射": 类别仍按 {@link CategoryNormalizer} 兜底,
     * 也就是改造前的行为。</p>
     */
    public record BrandEntry(SupplierPriceList list, Map<String, SupplierPriceItem> items,
                             ValueAliasQuery.AliasRules rules) {

        public BrandEntry(SupplierPriceList list, Map<String, SupplierPriceItem> items) {
            this(list, items, ValueAliasQuery.AliasRules.EMPTY);
        }

        public BrandEntry {
            rules = rules == null ? ValueAliasQuery.AliasRules.EMPTY : rules;
        }
    }

    /** 全部品牌的选中结果。 */
    public record BrandSelection(Map<String, BrandEntry> byBrand) {

        public BrandEntry entryOf(String brandName) {
            return brandName == null ? null : byBrand.get(brandName.trim());
        }
    }
}
