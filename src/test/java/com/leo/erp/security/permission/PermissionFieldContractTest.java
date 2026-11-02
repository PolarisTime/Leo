package com.leo.erp.security.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：{@link PermissionField} 的取值必须来自 {@link PermissionCodes} 注册表。
 *
 * <p>回归背景：字段级脱敏此前用字符串字面量书写（{@code @PermissionField("sales-orders:read:amount")}），
 * 而对应常量 {@code PermissionCodes.SALES_ORDERS_READ_AMOUNT} 在生产代码里引用数为 0；
 * 一旦资源名或字段名调整，常量与注册表同步改完，注解静默失配——成本、单价会明文返回，
 * 且没有任何测试会红。本测试把「写死一个字面量」变成单测失败。</p>
 *
 * <p>扫描面是全仓 {@code com.leo.erp} 下的类（含 record 的组件与访问器），因此新增 DTO 自动纳入。</p>
 */
class PermissionFieldContractTest {

    @Test
    void everyPermissionFieldAnnotation_mustUseRegisteredPermissionCode() throws Exception {
        Set<String> registered = PermissionCodes.all();
        assertThat(registered)
                .as("权限目录不应为空，否则本契约测试没有校验依据")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        Set<String> declared = new LinkedHashSet<>();

        for (Class<?> type : scanAllClasses()) {
            for (Field field : type.getDeclaredFields()) {
                collect(type, field.getName(), field.getAnnotation(PermissionField.class), declared, violations, registered);
            }
            for (Method method : type.getDeclaredMethods()) {
                collect(type, method.getName(), method.getAnnotation(PermissionField.class), declared, violations, registered);
            }
            RecordComponent[] components = type.getRecordComponents();
            if (components != null) {
                for (RecordComponent component : components) {
                    collect(type, component.getName(), component.getAnnotation(PermissionField.class),
                            declared, violations, registered);
                }
            }
        }

        assertThat(declared)
                .as("应扫描到 @PermissionField 标注（当前为销售金额与库存成本两类），否则扫描范围失效")
                .contains(PermissionCodes.SALES_ORDERS_READ_AMOUNT, PermissionCodes.INVENTORY_READ_COST);
        assertThat(violations).isEmpty();
    }

    private void collect(Class<?> owner,
                         String member,
                         PermissionField annotation,
                         Set<String> declared,
                         List<String> violations,
                         Set<String> registered) {
        if (annotation == null) {
            return;
        }
        String code = annotation.value();
        declared.add(code);
        if (!registered.contains(code)) {
            violations.add(owner.getName() + "#" + member + " -> " + code
                    + "（未登记在 PermissionCodes，字段级脱敏将失效）");
        }
    }

    private List<Class<?>> scanAllClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        // 匹配所有具体类：契约要覆盖 DTO、实体以外的任意持有 @PermissionField 的类型。
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.leo.erp")) {
            classes.add(Class.forName(candidate.getBeanClassName()));
        }
        assertThat(classes)
                .as("应能扫描到业务类，否则本契约测试没有覆盖任何类型")
                .isNotEmpty();
        return classes;
    }
}
