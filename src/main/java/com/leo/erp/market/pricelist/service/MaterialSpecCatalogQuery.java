package com.leo.erp.market.pricelist.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规格全集查询(只读)。
 * <p>来源 {@code md_material}({@code deleted_flag = false})去重, 去重键 = 类别 + 材质 + 规格归一化数字 + 定尺。</p>
 *
 * <p>归一化口径必须与既有生成列一致, 否则两端条目键会漂移:</p>
 * <ul>
 *   <li>规格: {@code regexp_replace(spec, '[^0-9]', '')}, 即 {@code md_material.spec_sort}。
 *       不含数字的值(如 {@code 无}/{@code 其他})归一化后为空 → <b>整行跳过</b>, 绝不当作 0;</li>
 *   <li>定尺: 价格条目列为 {@code varchar(16)}, 而 {@code md_material.length} 为 {@code varchar(32)},
 *       超长按 16 截断, 保证与既有单据行/价格条目键一致;</li>
 *   <li>归一化后重复的键({@code Φ12} 与 {@code 12})只保留一行;</li>
 *   <li>排序固定 {@code category, material, spec_sort, length_sort}。</li>
 * </ul>
 */
@Component
public class MaterialSpecCatalogQuery {

    /** 价格条目定尺列长度上限(varchar(16)), 与 mk_supplier_price_item.length 一致。 */
    static final int LENGTH_MAX = 16;

    /**
     * 全量/按类别材质筛选的规格全集查询。
     *
     * <p>{@code cast(? as varchar)} 是必需的: PostgreSQL 无法从
     * {@code ? is null} 推断无类型参数, 不加显式转型会报
     * {@code could not determine data type of parameter $1}(真库才会暴露, mock 测不到)。
     * 值仍走绑定占位符, 不做字符串拼接。</p>
     */
    private static final String SQL = """
            select m.category as category,
                   m.material as material,
                   m.spec as spec_raw,
                   m.spec_sort as spec_sort,
                   m.length as length_raw
            from public.md_material m
            where m.deleted_flag = false
              and m.spec_sort is not null
              and (cast(? as varchar) is null or m.category = ?)
              and (cast(? as varchar) is null or m.material = ?)
            order by m.category asc, m.material asc, m.spec_sort asc, m.length_sort asc, m.spec asc, m.length asc
            """;

    private final JdbcTemplate jdbcTemplate;

    public MaterialSpecCatalogQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 全量全集(不带筛选)。 */
    @Transactional(readOnly = true)
    public List<MaterialSpecSnapshot> findAll() {
        return find(null, null);
    }

    /**
     * 按类别/材质筛选全集。
     *
     * @param category 类别, null/空表示不筛选
     * @param material 材质, null/空表示不筛选
     */
    @Transactional(readOnly = true)
    public List<MaterialSpecSnapshot> find(String category, String material) {
        String categoryFilter = blankToNull(category);
        String materialFilter = blankToNull(material);
        List<MaterialSpecSnapshot> rows = jdbcTemplate.query(
                SQL,
                (rs, rowNum) -> new MaterialSpecSnapshot(
                        trimToNull(rs.getString("category")),
                        trimToNull(rs.getString("material")),
                        (Integer) rs.getObject("spec_sort"),
                        truncate(rs.getString("length_raw")),
                        0),
                categoryFilter, categoryFilter, materialFilter, materialFilter);
        Map<String, MaterialSpecSnapshot> unique = new LinkedHashMap<>();
        List<MaterialSpecSnapshot> ordered = new ArrayList<>();
        int sortOrder = 0;
        for (MaterialSpecSnapshot row : rows) {
            if (row.category() == null || row.material() == null || row.spec() == null) {
                // 规格不含数字(归一化后为空)或类别/材质为空: 无法作为条目键, 整行跳过
                continue;
            }
            String key = key(row.category(), row.material(), row.spec(), row.length());
            if (unique.putIfAbsent(key, row) != null) {
                continue;
            }
            ordered.add(new MaterialSpecSnapshot(row.category(), row.material(), row.spec(), row.length(), sortOrder));
            sortOrder += 1;
        }
        return List.copyOf(ordered);
    }

    /** 规格全集键(与价格条目键同口径), 供创建/更新版本时校验。 */
    public static String key(String category, String material, Integer spec, String length) {
        return (category == null ? "" : category.trim()) + "|"
                + (material == null ? "" : material.trim()) + "|"
                + (spec == null ? "" : spec) + "|"
                + (length == null ? "" : length);
    }

    /** 由 {@code md_material.length} 抽取定尺数字(仅用于排序/展示辅助), 无数字时返回空串。 */
    public static String lengthSortKey(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("[^0-9.]", "");
        return digits.isEmpty() ? "" : digits;
    }

    private static String truncate(String value) {
        String trimmed = value == null ? "" : value.trim();
        // 定尺保留原样(如 "9米"), 只做长度截断, 保证与 mk_quote_item.length / 价格条目 length 字符串一致
        return trimmed.length() <= LENGTH_MAX ? trimmed : trimmed.substring(0, LENGTH_MAX);
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

    /** 规格全集快照。 */
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
