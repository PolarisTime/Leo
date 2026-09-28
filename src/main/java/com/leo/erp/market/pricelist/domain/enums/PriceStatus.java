package com.leo.erp.market.pricelist.domain.enums;

import java.util.Locale;

/**
 * 价格表条目报价状态。
 * <p>稀疏矩阵里"不报价"由 {@code price IS NULL} 表达, 本枚举只描述条目的供应状况:</p>
 * <ul>
 *   <li>{@link #NORMAL} 正常报价;</li>
 *   <li>{@link #PENDING} 在途/待卸;</li>
 *   <li>{@link #BUNDLED} 搭配(需搭售其他规格);</li>
 *   <li>{@link #NEGOTIABLE} 价格单议;</li>
 *   <li>{@link #OUT_OF_STOCK} 无货/不报价。</li>
 * </ul>
 */
public enum PriceStatus {
    NORMAL,
    PENDING,
    BUNDLED,
    NEGOTIABLE,
    OUT_OF_STOCK;

    /** 缺省状态: 请求未携带或空白时按正常报价处理。 */
    public static PriceStatus resolveOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        return parse(value);
    }

    /**
     * 严格解析: 大小写不敏感, 非法值抛 {@link IllegalArgumentException}(由服务层转 422)。
     */
    public static PriceStatus parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("报价状态不合法: " + value);
        }
    }
}
