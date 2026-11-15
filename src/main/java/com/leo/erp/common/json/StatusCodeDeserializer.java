package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.leo.erp.common.support.StatusConstants;

import java.io.IOException;

/**
 * 状态字段反序列化器：请求体接受稳定英文 code，并归一化为 code（DB 与领域层的唯一存值）。
 *
 * <p>迁移期同时接受旧中文 label（{@code 已审核} → {@code AUDITED}、{@code 报价} → {@code QUOTING}），
 * 未知状态（如既有非状态文本）原样返回，保证旧客户端不被破坏；空字符串返回 {@code null}。</p>
 */
public class StatusCodeDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return StatusConstants.toCode(normalized);
    }
}
