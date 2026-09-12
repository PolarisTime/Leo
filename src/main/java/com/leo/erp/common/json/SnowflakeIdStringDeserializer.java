package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;

/**
 * 雪花 ID 请求字段专用反序列化：只接受十进制字符串。
 *
 * <p>JavaScript Number 无法精确表示 19 位雪花 ID，若请求体接受 JSON number，客户端一旦误用
 * Number 就会静默丢失低位。全局 {@code SnowflakeSafeLongDeserializer} 仍兼容 ≤ 2^53-1 的历史
 * 数值入参，本反序列化器用于已明确收紧契约的接口字段，直接拒绝任何数值形式。
 */
public class SnowflakeIdStringDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            throw InvalidFormatException.from(p, "雪花 ID 必须以十进制字符串传递", p.getText(), Long.class);
        }
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                long value = Long.parseLong(text);
                if (value <= 0) {
                    throw InvalidFormatException.from(p, "雪花 ID 必须为正整数", text, Long.class);
                }
                return value;
            } catch (NumberFormatException e) {
                throw InvalidFormatException.from(p, "雪花 ID 必须为十进制整数字符串", text, Long.class);
            }
        }
        return (Long) ctxt.handleUnexpectedToken(Long.class, p);
    }
}
