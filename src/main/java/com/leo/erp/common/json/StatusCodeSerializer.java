package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.leo.erp.common.support.StatusConstants;

import java.io.IOException;

/**
 * 状态字段序列化器：把数据库/领域层的状态值统一输出为稳定英文 code。
 *
 * <p>V219 之后数据库已存 code，正常路径是「原样输出」；仍保留旧中文 label →
 * code 的兼容映射（{@code 完成销售} → {@code SALES_COMPLETED}、{@code 已付款} → {@code PAID}、
 * {@code 成功} → {@code SUCCESS}），用于回滚环境、旧快照与非单据状态。未知值原样输出，
 * 避免把非状态文本误映射。空值输出 {@code null}。</p>
 */
public class StatusCodeSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(StatusConstants.toCode(value));
    }
}
