package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.api.LengthSpecCatalog;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 打印导出共用的商品规格拼接规则：按定尺主数据的「打印规格后缀」在规格后追加内容
 * （如 12米 → {@code *12}，9米 → 不追加）。
 *
 * <p>此前该映射写死在 {@code PrintSpecLengthRule} 的 Java 常量里，新增定尺或调整后缀都要改代码
 * 重启；现在后缀来自 {@code md_length_premium.spec_suffix}，由定尺加价维护统一维护。</p>
 */
@Component
public class PrintSpecLengthResolver {

    /**
     * 迁移兜底：定尺主数据为空（未初始化 / 回滚到改造前的库）时沿用改造前写死的
     * 「12米 → *12」，保证打印结果与升级前一致。
     *
     * <p><b>这不是业务来源</b>——正常路径的取值一律来自定尺主数据的 {@code spec_suffix}。</p>
     */
    private static final String LEGACY_LENGTH_NAME = "12米";
    private static final String LEGACY_SPEC_SUFFIX = "*12";

    /** 与行情匹配（{@code SteelQuoteMatchService.resolveLengthPremium}）一致的数字归一后缀。 */
    private static final String LENGTH_METER_SUFFIX = "米";

    private final LengthSpecCatalog lengthSpecCatalog;

    public PrintSpecLengthResolver(LengthSpecCatalog lengthSpecCatalog) {
        this.lengthSpecCatalog = lengthSpecCatalog;
    }

    /**
     * 按定尺后缀拼接规格：spec 为空时原样返回空串；定尺没有后缀、或规格已经以该后缀结尾时不重复追加。
     */
    public String apply(String spec, String length) {
        String safeSpec = spec == null ? "" : spec.trim();
        String safeLength = length == null ? "" : length.trim();
        if (safeSpec.isEmpty()) {
            return safeSpec;
        }
        Map<String, String> suffixByLength = lengthSpecCatalog.activeSpecSuffixMap();
        if (suffixByLength.isEmpty() && lengthSpecCatalog.activeLengthNames().isEmpty()) {
            return appendLegacySuffix(safeSpec, safeLength);
        }
        String suffix = resolveSuffix(suffixByLength, safeLength);
        if (suffix.isEmpty() || safeSpec.endsWith(suffix)) {
            return safeSpec;
        }
        return safeSpec + suffix;
    }

    /** 后缀匹配口径与行情匹配一致：先按定尺名称精确匹配，再统一比较米数（兼容 "12 米" 等写法）。 */
    private String resolveSuffix(Map<String, String> suffixByLength, String length) {
        if (length.isEmpty()) {
            return "";
        }
        String exact = suffixByLength.get(length);
        if (exact != null) {
            return exact;
        }
        String digits = length.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, String> entry : suffixByLength.entrySet()) {
            String normalizedName = entry.getKey().replaceAll("[^0-9" + LENGTH_METER_SUFFIX + "]", "");
            if (normalizedName.equals(digits + LENGTH_METER_SUFFIX) && entry.getValue() != null) {
                return entry.getValue();
            }
        }
        return "";
    }

    private String appendLegacySuffix(String safeSpec, String safeLength) {
        if (!LEGACY_LENGTH_NAME.equals(safeLength) || safeSpec.endsWith(LEGACY_SPEC_SUFFIX)) {
            return safeSpec;
        }
        return safeSpec + LEGACY_SPEC_SUFFIX;
    }
}
