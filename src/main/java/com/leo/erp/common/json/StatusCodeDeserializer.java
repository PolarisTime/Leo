package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.leo.erp.common.support.DocumentStatus;

import java.io.IOException;

/**
 * 状态字段反序列化器：请求体接受稳定英文 code，并归一化为数据库/领域层使用的中文 label。
 *
 * <p>迁移期同时接受中文 label（原样返回）与未知状态（例如 {@code SUCCESS}），
 * 保证旧客户端与操作日志等非单据状态不被破坏；空字符串返回 {@code null}。</p>
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
        return DocumentStatus.fromCode(normalized)
                .map(DocumentStatus::label)
                .orElse(normalized);
    }
}
