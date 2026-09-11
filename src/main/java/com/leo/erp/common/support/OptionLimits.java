package com.leo.erp.common.support;

import java.util.List;

/**
 * 下拉/选项类只读接口的响应上界，避免返回无界列表。
 */
public final class OptionLimits {

    public static final int MAX_OPTIONS = 2000;

    private OptionLimits() {
    }

    public static <T> List<T> cap(List<T> options) {
        if (options == null || options.size() <= MAX_OPTIONS) {
            return options;
        }
        return options.stream().limit(MAX_OPTIONS).toList();
    }
}
