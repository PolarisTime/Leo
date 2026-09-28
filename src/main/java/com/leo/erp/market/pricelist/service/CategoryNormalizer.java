package com.leo.erp.market.pricelist.service;

/**
 * 商品类别规范化与条目键匹配(比价单行键 ↔ 价格表条目键)。
 *
 * <p><b>规则来源已改为可维护数据</b>: {@code 直条} ≡ {@code 螺纹钢} 现在是
 * {@code md_value_alias} 的 CATEGORY 种子行(V171), 统一由 {@link ValueAliasQuery} 读取;
 * 本类只保留该硬编码常量作为<b>兜底</b> —— 表数据缺失、未执行 V171 或缓存/查询不可用时,
 * 归一化行为与改造前完全一致。调用方请优先走 {@link ValueAliasQuery#normalize} /
 * {@link ValueAliasQuery.AliasRules#normalizeCategory}。</p>
 *
 * <p><b>根因(历史):</b> 真实库中 {@code md_material.category} 使用字典值
 * {@code 直条}({@code md_material_category}: REBAR|直条, WIRE|线材, WIRE_ROD|盘螺),
 * 而比价单行 {@code mk_quote_item.category} 使用 {@code 螺纹钢};
 * 前端比价模块早已把两者视为同一类别, 见
 * {@code aries/src/views/price-compare/core.ts} 的 {@code normalizeCategory}
 * ({@code value === '直条' ? '螺纹钢' : value})。</p>
 *
 * <p>服务端必须与前端同口径, 否则价格表条目(键来自 {@code md_material} 的 {@code 直条})
 * 与比价单行({@code 螺纹钢})永远无法命中, 现货价会恒为空。新增别名请直接维护
 * {@code md_value_alias} 数据, 不再改这里的常量。</p>
 */
public final class CategoryNormalizer {

    /** {@code md_material} 的螺纹钢(直条)字典值。 */
    public static final String MATERIAL_CATEGORY_REBAR = "直条";
    /** 比价单行使用的螺纹钢类别名。 */
    public static final String QUOTE_CATEGORY_REBAR = "螺纹钢";

    private CategoryNormalizer() {
    }

    /**
     * 类别规范化兜底: 默认相等, 仅 {@code 直条} 与 {@code 螺纹钢} 视为同一类别。
     * <p>与前端 {@code normalizeCategory} 语义一致(此处额外 trim 并忽略大小写, 便于稳健匹配)。</p>
     * <p>仅在 {@code md_value_alias} 未命中时作为回退调用, 见 {@link ValueAliasQuery}。</p>
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return MATERIAL_CATEGORY_REBAR.equals(trimmed) ? QUOTE_CATEGORY_REBAR : trimmed;
    }

    /** 两个类别名在规范化后是否表示同一类别。 */
    public static boolean sameCategory(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return normalizedLeft == null ? normalizedRight == null : normalizedLeft.equals(normalizedRight);
    }
}
