package com.leo.erp.common.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.leo.erp.common.validation.SnowflakeId;

import java.util.Iterator;

/**
 * 把请求体中带 {@link SnowflakeId} 的 {@code Long} 字段统一收紧为「只接受十进制字符串」。
 *
 * <p>背景：全局 {@code SnowflakeSafeLongDeserializer} 为了让历史客户端平滑迁移，仍接受
 * ≤ 2^53−1 的 JSON number；但 AGENTS 明确要求「新增 API 不得接受 JSON number 形式的雪花 ID」。
 * 逐字段写 {@code @JsonDeserialize(using = SnowflakeIdStringDeserializer.class)} 容易漏写，
 * 这里用 Jackson 官方扩展点 {@link BeanDeserializerModifier} 在 bean 反序列化器构建阶段
 * 按注解统一装配 {@link SnowflakeIdStringDeserializer}：</p>
 * <ul>
 *   <li>语义落在单一位置（一个模块），DTO 只需标注 {@code @SnowflakeId} 即同时获得
 *       「正数校验（Bean Validation）+ 拒绝 JSON number（Jackson）」两种入站边界；</li>
 *   <li>非 ID 的 {@code Long}（行数、文件大小、版本号等）行为不变，仍走全局宽松反序列化；</li>
 *   <li>已显式标注 {@code @JsonDeserialize} 的字段重复装配同一反序列化器，等价且幂等。</li>
 * </ul>
 */
public class SnowflakeIdStrictModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public SnowflakeIdStrictModule() {
        super("snowflake-id-strict-module");
        setDeserializerModifier(new SnowflakeIdStrictModifier());
    }

    static class SnowflakeIdStrictModifier extends BeanDeserializerModifier {

        @Override
        public BeanDeserializerBuilder updateBuilder(
                DeserializationConfig config,
                BeanDescription beanDesc,
                BeanDeserializerBuilder builder
        ) {
            Iterator<SettableBeanProperty> properties = builder.getProperties();
            while (properties.hasNext()) {
                SettableBeanProperty property = properties.next();
                if (requiresStringOnlyId(property)) {
                    builder.addOrReplaceProperty(
                            property.withValueDeserializer(new SnowflakeIdStringDeserializer()), true);
                }
            }
            return builder;
        }

        private boolean requiresStringOnlyId(SettableBeanProperty property) {
            if (property.getAnnotation(SnowflakeId.class) == null) {
                return false;
            }
            Class<?> rawType = property.getType().getRawClass();
            return rawType == Long.class || rawType == long.class;
        }
    }
}
