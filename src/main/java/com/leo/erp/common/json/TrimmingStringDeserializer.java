package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;
import java.util.Set;

/**
 * 请求体字符串统一 trim。
 *
 * <p>Bean Validation 的 {@code @Size} 等约束在反序列化之后执行，若其按未归一化的原始字符串
 * 判定长度，会导致「首尾带空格但 trim 后合法」的值被误拒。此反序列化器在进入校验前完成
 * {@code trim}，使长度与内容约束都按归一化后的值判定，与 Service 层的写入归一化保持一致。
 *
 * <p>密码类字段保留原值：首尾空格可能是有意义的口令字符。
 */
public class TrimmingStringDeserializer extends JsonDeserializer<String> implements ContextualDeserializer {

    private static final Set<String> PRESERVED_PROPERTIES = Set.of(
            "password", "currentPassword", "oldPassword", "newPassword", "rawPassword");

    private final boolean trim;

    public TrimmingStringDeserializer() {
        this(true);
    }

    private TrimmingStringDeserializer(boolean trim) {
        this.trim = trim;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        if (property != null && PRESERVED_PROPERTIES.contains(property.getName())) {
            return new TrimmingStringDeserializer(false);
        }
        return this;
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || !trim) {
            return value;
        }
        return value.trim();
    }
}
