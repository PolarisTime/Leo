package com.leo.erp.security.permission;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.leo.erp.common.json.FieldPrecisionResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 字段级权限感知的 {@link BigDecimal} 序列化器。
 *
 * <ul>
 *   <li>字段标注 {@link PermissionField} 且当前用户不具备该权限码时，输出 {@code null}；</li>
 *   <li>其余情况按属性名推断精度（与全局 {@code ScaledBigDecimalSerializer} 一致：金额/单价/运费 2 位，重量按内部精度），保证输出不变。</li>
 * </ul>
 *
 * <p>精度规则统一委托 {@link FieldPrecisionResolver}：两条序列化路径各写一份会漂移，
 * 表现为同一字段「有权限时 2 位、其他出口 N 位」。</p>
 *
 * <p>用于响应 DTO 的金额/成本字段，集中实现"无权限则不可见"，无需在装配层逐个重建 record。</p>
 */
public class PermissionDecimalSerializer extends StdScalarSerializer<BigDecimal>
        implements ContextualSerializer {

    private final String requiredPermission;
    private final Integer scale;

    public PermissionDecimalSerializer() {
        this(null, null);
    }

    private PermissionDecimalSerializer(String requiredPermission, Integer scale) {
        super(BigDecimal.class);
        this.requiredPermission = requiredPermission;
        this.scale = scale;
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (requiredPermission != null && !PermissionChecker.hasCurrent(requiredPermission)) {
            gen.writeNull();
            return;
        }
        BigDecimal normalized = scale == null ? value : value.setScale(scale, RoundingMode.HALF_UP);
        gen.writeNumber(normalized.toPlainString());
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property == null) {
            return this;
        }
        PermissionField annotation = property.getAnnotation(PermissionField.class);
        String required = annotation == null ? null : annotation.value();
        Integer resolvedScale = FieldPrecisionResolver.resolveScale(property.getName());
        return new PermissionDecimalSerializer(required, resolvedScale);
    }
}
