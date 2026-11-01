package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.leo.erp.common.support.PrecisionConstants;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 使用率（比值，非金额）出参序列化：固定 4 位小数、HALF_UP。
 *
 * <p>为什么必须字段级指定：全局 {@link ScaledBigDecimalSerializer} 按字段名启发式决定精度，
 * {@code amountUsageRate} 命中 {@code amount} → 被强制截到 2 位，
 * 会把 {@code 0.8532}（85.32%）静默输出成 {@code 0.85}（85.00%）；
 * 而 {@code tonnageUsageRate} 不含 {@code amount} / {@code weight} 又保持 4 位，
 * 同一个响应里两个使用率精度不一致。使用率字段一律用本序列化器显式钉住精度。</p>
 */
public class UsageRateSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        BigDecimal normalized = value.setScale(PrecisionConstants.USAGE_RATE_SCALE, RoundingMode.HALF_UP);
        gen.writeNumber(normalized.toPlainString());
    }
}
