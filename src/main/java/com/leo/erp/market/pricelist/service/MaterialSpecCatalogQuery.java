package com.leo.erp.market.pricelist.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规格键字典查询(只读, 实时投影)。
 *
 * <p><b>字典来源(常规来源, 每次查询现算)</b>:</p>
 * <ol>
 *   <li>{@code md_material}({@code deleted_flag = false})<b>去掉品牌</b>后的规格键去重
 *       —— 商品信息是有品牌的物料档案, 比价是"无品牌行键 + 品牌作列", 因此必须去品牌、与比价行键同构;
 *       以商品信息打底可提前铺满规格(没比价过的规格也能先录价);</li>
 *   <li>{@code mk_quote_item} 实际出现过的行键, 仅取未软删单据({@code mk_quote_sheet.deleted_flag = false})
 *       的商品行 —— 比价单用过、但商品信息里没有的键也在字典内。</li>
 * </ol>
 * <p>商品信息新增物料后字典自动多行, 不需要迁移或手工同步(实时投影, 无缓存)。</p>
 *
 * <p><b>归一化三件套(只在本类实现, 字典 / 写库 / 比价行键匹配共用同一口径)</b>:</p>
 * <ul>
 *   <li>类别: {@link CategoryNormalizer#normalize}({@code 直条} ≡ {@code 螺纹钢});</li>
 *   <li>规格: 取数字(商品信息侧直接用生成列 {@code spec_sort}, 即 {@code Φ12} → {@code 12};
 *       比价单行的 {@code spec} 本身是整数); 无数字或 {@code <= 0} 的键不可用, 整行跳过;</li>
 *   <li>定尺: {@code -} / 空串 / NULL 视为同一含义(无定尺, 统一空串); {@code 9m}/{@code 9M}/{@code 9 米}
 *       归一为 {@code 9米}; 最后按条目列宽 {@code varchar(16)} 截断, 见 {@link #normalizeLength(String)}。</li>
 * </ul>
 *
 * <p>排序固定 类别 → 材质 → 规格(数值) → 定尺(数值优先, 非数值末尾)。</p>
 */
@Component
public class MaterialSpecCatalogQuery {

    /** 价格条目定尺列长度上限(varchar(16)), 与 mk_supplier_price_item.length 一致。 */
    static final int LENGTH_MAX = 16;

    /**
     * 字典来源一: 商品信息去品牌后的规格键。
     * <p>{@code select distinct} 不含 brand 列, 因此同一 (类别, 材质, 规格, 定尺) 的不同品牌只出一个键;
     * 规格经生成列 {@code spec_sort} 归一为数字, 不含数字的行({@code spec_sort is null})整行跳过。</p>
     */
    private static final String MATERIAL_DICTIONARY_SQL = """
            select distinct m.category as category,
                   m.material as material,
                   m.spec_sort as spec,
                   m.length as length_raw
            from public.md_material m
            where m.deleted_flag = false
              and m.spec_sort is not null
            """;

    /** 字典来源二: 比价单实际出现过的商品行键(仅未软删单据的商品行)。 */
    private static final String QUOTE_ITEM_SQL = """
            select distinct i.category as category,
                   i.material as material,
                   i.spec as spec,
                   i.length as length
            from public.mk_quote_item i
            join public.mk_quote_sheet s on s.id = i.sheet_id
            where s.deleted_flag = false
              and i.row_type = 'PRODUCT'
              and i.category is not null
              and i.material is not null
              and i.spec is not null
            """;

    private final JdbcTemplate jdbcTemplate;

    public MaterialSpecCatalogQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 全量字典(不带筛选)。 */
    @Transactional(readOnly = true)
    public List<MaterialSpecSnapshot> findAll() {
        return find(null, null);
    }

    /**
     * 按类别/材质筛选字典。
     *
     * <p>类别比较走 {@link CategoryNormalizer#sameCategory}({@code 直条} ≡ {@code 螺纹钢});
     * 材质按 trim 后精确比较。</p>
     *
     * @param category 类别, null/空表示不筛选
     * @param material 材质, null/空表示不筛选
     */
    @Transactional(readOnly = true)
    public List<MaterialSpecSnapshot> find(String category, String material) {
        List<MaterialSpecSnapshot> rows = new ArrayList<>(rowsFromMaterialDictionary());
        rows.addAll(rowsFromQuoteItems());
        return ordered(filter(rows, category, material));
    }

    /** 商品信息(去品牌)规格键。 */
    private List<MaterialSpecSnapshot> rowsFromMaterialDictionary() {
        return jdbcTemplate.query(MATERIAL_DICTIONARY_SQL,
                (rs, rowNum) -> new MaterialSpecSnapshot(
                        normalizeCategory(rs.getString("category")),
                        trimToNull(rs.getString("material")),
                        (Integer) rs.getObject("spec"),
                        normalizeLength(rs.getString("length_raw")),
                        0));
    }

    /** 比价单实际行键。 */
    private List<MaterialSpecSnapshot> rowsFromQuoteItems() {
        return jdbcTemplate.query(QUOTE_ITEM_SQL,
                (rs, rowNum) -> new MaterialSpecSnapshot(
                        normalizeCategory(rs.getString("category")),
                        trimToNull(rs.getString("material")),
                        (Integer) rs.getObject("spec"),
                        normalizeLength(rs.getString("length")),
                        0));
    }

    /** 类别/材质/规格必须齐全且规格为正, 否则无法作为条目键。 */
    private static boolean isUsable(MaterialSpecSnapshot row) {
        return row.category() != null && row.material() != null
                && row.spec() != null && row.spec() > 0;
    }

    private static List<MaterialSpecSnapshot> filter(List<MaterialSpecSnapshot> rows,
                                                     String category, String material) {
        String materialFilter = blankToNull(material);
        List<MaterialSpecSnapshot> result = new ArrayList<>(rows.size());
        for (MaterialSpecSnapshot row : rows) {
            if (!isUsable(row)) {
                continue;
            }
            if (category != null && !category.isBlank()
                    && !CategoryNormalizer.sameCategory(row.category(), category)) {
                continue;
            }
            if (materialFilter != null && !materialFilter.equals(row.material())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 去重 + 固定排序 + 连续 sortOrder。
     * <p>键 = 归一化类别 + 材质 + 规格 + 归一化定尺; 排序 类别 → 材质 → 规格(数值) → 定尺(数值优先)。</p>
     */
    private static List<MaterialSpecSnapshot> ordered(List<MaterialSpecSnapshot> rows) {
        Map<String, MaterialSpecSnapshot> unique = new LinkedHashMap<>();
        for (MaterialSpecSnapshot row : rows) {
            unique.putIfAbsent(key(row.category(), row.material(), row.spec(), row.length()), row);
        }
        List<MaterialSpecSnapshot> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator
                .comparing(MaterialSpecSnapshot::category, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MaterialSpecSnapshot::material, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MaterialSpecSnapshot::spec, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(snapshot -> lengthSortValue(snapshot.length()))
                .thenComparing(MaterialSpecSnapshot::length, Comparator.nullsLast(Comparator.naturalOrder())));
        List<MaterialSpecSnapshot> result = new ArrayList<>(sorted.size());
        int sortOrder = 0;
        for (MaterialSpecSnapshot row : sorted) {
            result.add(new MaterialSpecSnapshot(row.category(), row.material(), row.spec(), row.length(), sortOrder));
            sortOrder++;
        }
        return List.copyOf(result);
    }

    /**
     * 定尺归一(唯一定尺口径, 字典 / 写库 / 比价行键匹配都复用本方法):
     *
     * <ul>
     *   <li>NULL / 空串 / 只见空白 / {@code -} → 空串(无定尺概念);</li>
     *   <li>去掉所有空白后形如 {@code 数字 + m/M/米} → 统一 {@code 数字米}
     *       (如 {@code 9m}/{@code 9M}/{@code 9 米} → {@code 9米});</li>
     *   <li>其他写法(含纯数字、{@code 无} 等)原样保留(不做猜测);</li>
     *   <li>最后按价格条目列宽 {@code varchar(16)} 截断。</li>
     * </ul>
     */
    public static String normalizeLength(String raw) {
        if (raw == null) {
            return "";
        }
        String compact = raw.replaceAll("\\s+", "");
        if (compact.isEmpty() || "-".equals(compact)) {
            return "";
        }
        String normalized = compact;
        if (compact.matches("\\d+(\\.\\d+)?[mM米]")) {
            normalized = compact.substring(0, compact.length() - 1) + "米";
        }
        return normalized.length() <= LENGTH_MAX ? normalized : normalized.substring(0, LENGTH_MAX);
    }

    /** 规格键(与价格条目键同口径), 供创建/更新时校验。 */
    public static String key(String category, String material, Integer spec, String length) {
        return (category == null ? "" : category.trim()) + "|"
                + (material == null ? "" : material.trim()) + "|"
                + (spec == null ? "" : spec) + "|"
                + (length == null ? "" : length);
    }

    /** 抽取定尺中的数字(仅用于排序辅助), 无数字时返回空串。 */
    public static String lengthSortKey(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("[^0-9.]", "");
        return digits.isEmpty() ? "" : digits;
    }

    /** 定尺数值(用于排序): 数字部分转 BigDecimal, 非数值按 -1 参与比较(即非数值末尾)。 */
    private static BigDecimal lengthSortValue(String raw) {
        String digits = lengthSortKey(raw);
        if (digits.isEmpty()) {
            return BigDecimal.valueOf(-1);
        }
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException ex) {
            return BigDecimal.valueOf(-1);
        }
    }

    private static String normalizeCategory(String raw) {
        return CategoryNormalizer.normalize(trimToNull(raw));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 规格键快照。 */
    public record MaterialSpecSnapshot(
            String category,
            String material,
            Integer spec,
            String length,
            int sortOrder
    ) {
        /** 兼容不带排序号的构造(排序号由查询按排序结果补齐)。 */
        public MaterialSpecSnapshot(String category, String material, Integer spec, String length) {
            this(category, material, spec, length, 0);
        }
    }
}
