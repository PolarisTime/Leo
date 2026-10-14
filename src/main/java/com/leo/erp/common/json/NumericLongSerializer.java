package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 把 Long 数值字段按 JSON number 输出，用于覆盖 {@code JacksonConfig} 的全局
 * {@code Long -> 十进制字符串} 规则。
 *
 * <p>该全局规则只服务于雪花 ID 的 JavaScript 精度保护；文件大小、件数等普通数值
 * 一旦被序列化成字符串，按 number 声明契约的调用方（例如前端 Zod schema）会整条
 * 校验失败。字段级 {@code @JsonSerialize(using = NumericLongSerializer.class)}
 * 的优先级高于 {@code serializerByType}，因此可以精确豁免单个字段。</p>
 *
 * <p>使用前提：字段取值远小于 {@code 2^53 - 1}，作为 JSON number 不会丢精度。</p>
 */
public class NumericLongSerializer extends JsonSerializer<Long> {

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeNumber(value);
    }
}
