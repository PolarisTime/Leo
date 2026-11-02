package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 全局 {@link BigDecimal} 序列化器：按属性名推断精度后输出定点字符串。
 *
 * <p>精度规则不在本类内实现，统一委托 {@link FieldPrecisionResolver}——字段级权限脱敏
 * （{@code PermissionDecimalSerializer}）走的是同一条规则，两处各写一份会漂移。</p>
 */
public class ScaledBigDecimalSerializer extends StdScalarSerializer<BigDecimal> implements ContextualSerializer {

    private final Integer scale;

    public ScaledBigDecimalSerializer() {
        this(null);
    }

    private ScaledBigDecimalSerializer(Integer scale) {
        super(BigDecimal.class);
        this.scale = scale;
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        BigDecimal normalized = scale == null ? value : value.setScale(scale, RoundingMode.HALF_UP);
        gen.writeNumber(normalized.toPlainString());
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property == null) {
            return this;
        }
        Integer resolvedScale = FieldPrecisionResolver.resolveScale(property.getName());
        return resolvedScale == null ? this : new ScaledBigDecimalSerializer(resolvedScale);
    }
}
