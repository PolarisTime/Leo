package com.leo.erp.market.pricelist.service;

/**
 * 商品类别规范化与条目键匹配(比价单行键 ↔ 价格表条目键)。
 *
 * <p><b>与前端同源的别名规则:</b> 真实库中 {@code md_material.category} 使用字典值
 * {@code 直条}({@code md_material_category}: REBAR|直条, WIRE|线材, WIRE_ROD|盘螺),
 * 而比价单行 {@code mk_quote_item.category} 使用 {@code 螺纹钢};
 * 前端比价模块早已把两者视为同一类别, 见
 * {@code aries/src/views/price-compare/core.ts} 的 {@code normalizeCategory}
 * ({@code value === '直条' ? '螺纹钢' : value})。</p>
 *
 * <p>服务端必须镜像同一条规则, 否则价格表条目(键来自 {@code md_material} 的 {@code 直条})
 * 与比价单行({@code 螺纹钢})永远无法命中, 现货价会恒为空。改动任一端的别名规则时,
 * 必须同步修改另一端的同名函数, 避免再次漂移。</p>
 */
public final class CategoryNormalizer {

    /** {@code md_material} 的螺纹钢(直条)字典值。 */
    public static final String MATERIAL_CATEGORY_REBAR = "直条";
    /** 比价单行使用的螺纹钢类别名。 */
    public static final String QUOTE_CATEGORY_REBAR = "螺纹钢";

    private CategoryNormalizer() {
    }

    /**
     * 类别规范化: 默认相等, 仅 {@code 直条} 与 {@code 螺纹钢} 视为同一类别。
     * <p>与前端 {@code normalizeCategory} 语义一致(此处额外 trim 并忽略大小写, 便于稳健匹配)。</p>
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
