package com.leo.erp.common.config;

import com.leo.erp.common.validation.SnowflakeId;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：所有 {@code @RequestParam} 的 {@code Long}/{@code long} 查询参数
 * 必须有正整数约束（{@link Positive} 或 {@link SnowflakeId}）。
 *
 * <p>回归背景（2026-11-14 后端审计 P2 #12）：查询参数 ID 校验在同一 controller 内不一致，
 * 例如 {@code settlementCompanyId} 已校验而 {@code counterpartyId} 未校验；缺少校验时
 * 负数/0 会穿透到 SQL。该测试把「漏校验」变成全仓扫描级的单测失败。</p>
 */
class QueryParameterValidationContractTest {

    @Test
    void longQueryParametersMustBePositiveValidated() throws Exception {
        List<Class<?>> controllers = scanControllers();
        Set<String> violations = new TreeSet<>();
        int longQueryParameterCount = 0;

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.getAnnotation(RequestParam.class) == null) {
                        continue;
                    }
                    Class<?> type = parameter.getType();
                    if (type != Long.class && type != long.class) {
                        continue;
                    }
                    longQueryParameterCount++;
                    if (parameter.getAnnotation(Positive.class) == null
                            && parameter.getAnnotation(SnowflakeId.class) == null) {
                        violations.add(controller.getSimpleName() + "#" + method.getName()
                                + "." + parameter.getName());
                    }
                }
            }
        }

        assertThat(longQueryParameterCount)
                .as("应扫描到足够多的 Long 查询参数，否则本契约测试没有覆盖真实接口面")
                .isGreaterThan(20);
        assertThat(violations)
                .as("以下 Long 查询参数缺少 @Positive/@SnowflakeId 校验：%s", violations)
                .isEmpty();
    }

    private List<Class<?>> scanControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.leo.erp")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (Modifier.isAbstract(type.getModifiers()) || type.getName().indexOf('$') >= 0) {
                continue;
            }
            controllers.add(type);
        }
        return controllers;
    }
}
