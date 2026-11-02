package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.support.PrecisionConstants;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 打印数据的数值与文本格式化（纯函数，无状态）。
 *
 * <p>从 {@code PrintScriptService} 抽出：这些函数只做「字符串 → BigDecimal」、
 * 求和、金额/数量文本化与空值规整，不含渲染状态，单独成类后可直接单测，
 * 也让 PrintScriptService 只保留流程编排。</p>
 *
 * <p>精度口径与全站一致：金额按 {@link PrecisionConstants#AMOUNT_SCALE} +
 * {@link PrecisionConstants#DEFAULT_ROUNDING} 输出；解析失败/空值按 0 处理，
 * 与历史行为保持一致。</p>
 */
final class PrintItemFormatting {

    private PrintItemFormatting() {
    }

    /** 按字段求和（十进制精确累加）。 */
    static BigDecimal sumDecimal(List<Map<String, String>> items, String key) {
        return items.stream()
                .map(item -> decimalOf(item.get(key)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 按字段求和并取整（件数合计）。 */
    static int sumInteger(List<Map<String, String>> items, String key) {
        return items.stream()
                .mapToInt(item -> decimalOf(item.get(key)).intValue())
                .sum();
    }

    /** 取第一个正数（如物流单单价）：全为 0/负数时返回 0。 */
    static BigDecimal firstPositiveDecimal(List<Map<String, String>> items, String key) {
        for (Map<String, String> item : items) {
            BigDecimal value = decimalOf(item.get(key));
            if (value.signum() > 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    /** 解析十进制文本：空值/非法值返回 0（与历史兜底一致）。 */
    static BigDecimal decimalOf(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    /** 金额文本：按分位精度四舍五入后输出普通十进制字符串。 */
    static String plainAmount(BigDecimal value) {
        return value == null
                ? "0.00"
                : value
                      .setScale(
                          PrecisionConstants.AMOUNT_SCALE,
                          PrecisionConstants.DEFAULT_ROUNDING
                      )
                      .toPlainString();
    }

    /** 数量文本：去掉尾随零后的普通十进制字符串。 */
    static String plainNumber(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /** 去空白：null 返回空串。 */
    static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
