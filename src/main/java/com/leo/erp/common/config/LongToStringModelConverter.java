package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;

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
 */
public class LongToStringModelConverter implements ModelConverter {

    private static final String TYPE_STRING = "string";

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema<?> schema = chain.next().resolve(type, context, chain);
        if (schema != null && isLong(type)) {
            schema.setType(TYPE_STRING);
        }
        return schema;
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
