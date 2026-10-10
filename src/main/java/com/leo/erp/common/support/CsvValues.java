package com.leo.erp.common.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 逗号分隔的多值字段读写支持(项目允许品牌/材质等主数据多选)。
 * <p>
 * 与 {@code mk_quote_project_config.products/designated_brands} 的既有约定一致: 数据库只存原样文本,
 * 应用层负责去空白、去重与空值归一, 避免同一份列表在服务与映射器里各写一遍清洗逻辑而漂移。
 */
public final class CsvValues {

    private CsvValues() {
    }

    /** 列表 → 逗号分隔文本: 去空白、去重(保持首次出现顺序)、空列表归一为 {@code null}。 */
    public static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    /** 逗号分隔文本 → 不可变列表: {@code null}/空白返回空列表, 忽略空片段与重复项。 */
    public static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return List.copyOf(result);
    }
}
