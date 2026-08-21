package com.leo.erp.system.printtemplate.service;

/**
 * 打印导出共用的商品规格拼接规则：
 * 长度为"12米"的商品在规格后追加"*12"，其余长度（如 9 米）不处理。
 */
public final class PrintSpecLengthRule {

    private static final String TWELVE_METER = "12米";
    private static final String TWELVE_SUFFIX = "*12";

    private PrintSpecLengthRule() {
    }

    /**
     * 长度为 12 米且规格尚未带 *12 后缀时追加；spec/length 为空或非 12 米时原样返回。
     */
    public static String apply(String spec, String length) {
        String safeSpec = spec == null ? "" : spec.trim();
        String safeLength = length == null ? "" : length.trim();
        if (safeSpec.isEmpty() || !TWELVE_METER.equals(safeLength) || safeSpec.endsWith(TWELVE_SUFFIX)) {
            return safeSpec;
        }
        return safeSpec + TWELVE_SUFFIX;
    }
}
