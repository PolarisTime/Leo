package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class ScaledBigDecimalSerializer extends JsonSerializer<BigDecimal> implements ContextualSerializer {

    private final Integer scale;

    public ScaledBigDecimalSerializer() {
        this(null);
    }

    private ScaledBigDecimalSerializer(Integer scale) {
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
        Integer resolvedScale = resolveScale(property.getName());
        return resolvedScale == null ? this : new ScaledBigDecimalSerializer(resolvedScale);
    }

    private Integer resolveScale(String propertyName) {
        String normalizedName = propertyName == null ? "" : propertyName.toLowerCase(Locale.ROOT);
        if (normalizedName.contains("weight")) {
            return 3;
        }
        if (normalizedName.contains("amount") || normalizedName.contains("price") || normalizedName.contains("freight")) {
            return 2;
        }
        return null;
    }
}
