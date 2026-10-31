package com.leo.erp.common.validation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：所有「语义为 ID」的 {@code @PathVariable Long} 必须带 {@link SnowflakeId}（内含 {@code @Positive}），
 * 且所在控制器必须有 {@code @Validated}，否则方法级约束不会生效。
 *
 * <p>回归背景：过去全仓 129 个 {@code @PathVariable Long} 只有 6 个带正数校验，
 * 「ID 语义错误」统一表现为 404（查不到），排障成本高于 422（参数非法）。
 * 本测试把「新写一个 {@code @PathVariable Long} 忘了加约束」变成单测失败，
 * 而不是靠评审记忆。</p>
 */
class SnowflakeIdPathVariableContractTest {

    @Test
    void everyLongPathVariableMustCarrySnowflakeIdConstraint() throws Exception {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : scanControllers()) {
            boolean validated = controller.isAnnotationPresent(Validated.class);
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (!parameter.isAnnotationPresent(PathVariable.class)) {
                        continue;
                    }
                    Class<?> type = parameter.getType();
                    if (type != Long.class && type != long.class) {
                        continue;
                    }
                    String location = controller.getSimpleName() + "#" + method.getName()
                            + "(" + parameter.getName() + ")";
                    if (!parameter.isAnnotationPresent(SnowflakeId.class)) {
                        violations.add(location + " 缺少 @SnowflakeId/@Positive");
                    }
                    if (!validated) {
                        violations.add(location + " 所在控制器缺少 @Validated，约束不会生效");
                    }
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    private List<Class<?>> scanControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.leo.erp")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            controllers.add(type);
        }
        assertThat(controllers)
                .as("应能扫描到 @RestController，否则本契约测试没有覆盖任何控制器")
                .isNotEmpty();
        return controllers;
    }
}
