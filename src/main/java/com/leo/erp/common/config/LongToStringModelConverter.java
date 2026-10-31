package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.JavaType;
import com.leo.erp.common.validation.SnowflakeId;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;

/**
 * 将 Long/long 的 OpenAPI schema 由 integer/int64 校正为 string/int64，
 * 与 {@link JacksonConfig} 使用 ToStringSerializer 将 Long 序列化为十进制字符串的对外契约保持一致。
 *
 * <p>先委托给 springdoc/swagger 默认解析链完成字段、注解、示例与缓存处理，
 * 再仅当解析类型确实为 Long/long 时改写 type，因此不会影响 Integer、Short、Byte、
 * BigDecimal 与日期时间等其它类型；对集合元素、Map 值等泛型位置同样生效。</p>
 *
 * <p>被 {@link SnowflakeId} 标注的字段额外补上正整数约束与 19 位示例
 * （{@code pattern: ^[1-9]\d{0,18}$}、19 位示例）：生成式客户端与契约检查据此知道
 * 「ID 是 1~19 位十进制正整数」，不会再把 {@code "0"}、{@code "-1"} 或浮点形式当合法 ID。
 * 非 ID 的 Long（行数、文件大小等）保持原有的 string/int64 形态，不附加 ID 约束，
 * 避免把 {@code totalElements} 之类误标为雪花 ID。</p>
 */
public class LongToStringModelConverter implements ModelConverter {

    private static final String TYPE_STRING = "string";

    /** 雪花 ID 的十进制形态：首位非零，最多 19 位（≤ Long.MAX_VALUE）。 */
    static final String SNOWFLAKE_ID_PATTERN = "^[1-9]\\d{0,18}$";

    /** 19 位示例值，提示调用方 ID 可能超出 JavaScript 安全整数范围。 */
    static final String SNOWFLAKE_ID_EXAMPLE = "1234567890123456789";

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema<?> schema = chain.next().resolve(type, context, chain);
        if (schema != null && isLong(type)) {
            schema.setType(TYPE_STRING);
            if (isSnowflakeId(type)) {
                schema.setPattern(SNOWFLAKE_ID_PATTERN);
                schema.setExample(SNOWFLAKE_ID_EXAMPLE);
            }
        }
        return schema;
    }

    private boolean isSnowflakeId(AnnotatedType type) {
        Annotation[] annotations = type.getCtxAnnotations();
        if (annotations == null) {
            return false;
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof SnowflakeId) {
                return true;
            }
        }
        return false;
    }

    private boolean isLong(AnnotatedType type) {
        if (type == null) {
            return false;
        }
        Class<?> rawClass = rawClass(type.getType());
        return rawClass == Long.class || rawClass == long.class;
    }

    private Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof JavaType javaType) {
            return javaType.getRawClass();
        }
        if (type instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }
}
