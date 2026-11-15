package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.leo.erp.common.support.DocumentStatus;

import java.io.IOException;

/**
 * 状态字段序列化器：把数据库/领域层的状态值统一输出为 {@link DocumentStatus} 的稳定英文 code。
 *
 * <p>兼容三种输入：中文 label（{@code 完成销售} → {@code SALES_COMPLETED}）、已经是 code
 * （原样输出）、以及不属于 {@link DocumentStatus} 的其它状态（例如操作日志的 {@code SUCCESS}，
 * 原样输出，避免把非单据状态误映射）。空值输出 {@code null}。</p>
 *
 * <p>该序列化器是「状态枚举改稳定 code」迁移的兼容层：数据库仍存中文 label，API 边界输出 code；
 * 后续阶段再迁移数据库值与前端词表。</p>
 */
public class StatusCodeSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        String code = DocumentStatus.fromLabel(value)
                .map(DocumentStatus::code)
                .orElse(value);
        gen.writeString(code);
    }
}
