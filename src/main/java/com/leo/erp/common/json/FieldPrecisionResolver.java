package com.leo.erp.common.json;

import com.leo.erp.common.support.PrecisionConstants;

import java.util.Locale;

/**
 * 按 <b>JSON 属性名</b> 推断 {@link java.math.BigDecimal} 输出精度的唯一实现。
 *
 * <p>这是全站金额/重量精度的「唯一来源」：{@link ScaledBigDecimalSerializer}（全局默认）
 * 与 {@code security.permission.PermissionDecimalSerializer}（字段级权限脱敏）都必须调用本类，
 * 否则同一个字段在「有权限」与「无权限」两条序列化路径上会得到不同精度。</p>
 *
 * <p>规则（命中顺序不可调换，先金额后重量）：属性名含 {@code amount|price|freight} → 2 位；
 * 含 {@code weight} → {@link PrecisionConstants#WEIGHT_SCALE}；其余不指定（按 {@code BigDecimal}
 * 原始 scale 输出，避免把 {@code 1E+3} 之类规范化成定点数）。</p>
 */
public final class FieldPrecisionResolver {

    private FieldPrecisionResolver() {
    }

    /**
     * @param propertyName JSON 属性名；{@code null} 视为不命中任何规则
     * @return 目标 scale；{@code null} 表示不调整，按原值输出
     */
    public static Integer resolveScale(String propertyName) {
        String normalizedName = propertyName == null ? "" : propertyName.toLowerCase(Locale.ROOT);
        if (normalizedName.contains("amount") || normalizedName.contains("price") || normalizedName.contains("freight")) {
            return 2;
        }
        if (normalizedName.contains("weight")) {
            return PrecisionConstants.WEIGHT_SCALE;
        }
        return null;
    }
}
