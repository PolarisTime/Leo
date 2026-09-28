package com.leo.erp.market.pricelist.domain.enums;

import java.util.Locale;

/** 价格表整表/选区加减方向。 */
public enum PriceAdjustmentMode {
    ADD,
    SUBTRACT;

    /** 严格解析: 大小写不敏感, 非法值抛 {@link IllegalArgumentException}(由服务层转 422)。 */
    public static PriceAdjustmentMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("加减方向不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("加减方向不合法: " + value);
        }
    }
}
