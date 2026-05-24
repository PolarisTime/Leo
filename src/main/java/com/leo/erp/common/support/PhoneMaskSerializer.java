package com.leo.erp.common.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class PhoneMaskSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.isBlank()) {
            gen.writeString(value);
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            gen.writeString(trimmed);
            return;
        }
        gen.writeString(trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4));
    }
}
