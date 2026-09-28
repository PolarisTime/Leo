package com.leo.erp.market.pricelist.domain.enums;

/**
 * 值映射维度(与 {@code md_value_alias.dimension} 的 CHECK 枚举一一对应)。
 *
 * <ul>
 *   <li>{@link #CATEGORY} 类别(如 {@code 直条} ≡ {@code 螺纹钢});</li>
 *   <li>{@link #MATERIAL} 材质(如 {@code 抗震钢E} 与 {@code HRB400E抗震钢} 为同一材质的两种写法);</li>
 *   <li>{@link #LENGTH} 定尺(如 {@code 9} ≡ {@code 9米});</li>
 *   <li>{@link #BRAND} 品牌/产地(价格表 {@code brand_name} × 单据品牌列)。</li>
 * </ul>
 *
 * <p><b>只做同一含义多写法归一, 不做跨语义合并</b>: 不同含义的值(如 {@code 螺纹钢} 与 {@code 盘螺})
 * 严禁通过本维度互相映射。</p>
 */
public enum ValueAliasDimension {
    CATEGORY,
    MATERIAL,
    LENGTH,
    BRAND;

    /**
     * 解析请求/查询参数中的维度文本(忽略首尾空白, 大小写不敏感)。
     *
     * @throws IllegalArgumentException 维度为空或不在四类之内
     */
    public static ValueAliasDimension parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("维度不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("维度只能是 CATEGORY/MATERIAL/LENGTH/BRAND 之一: " + raw);
        }
    }
}
