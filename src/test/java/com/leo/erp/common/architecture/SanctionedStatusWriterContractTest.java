package com.leo.erp.common.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：{@link SanctionedStatusWriter} 的豁免必须写明理由，且豁免面不得静默扩大或失效。
 *
 * <p>背景：状态写白名单此前按类名后缀（{@code *ApplyService} 等）放行，等于「改个类名就获得
 * 写实体状态权限」，构建期门禁看不出谁被豁免。改为注解后，注解本身成了唯一的豁免凭证，
 * 因此需要两件事兜底：理由不能为空（否则 diff 里看不出为什么），以及注解数量不能悄悄变成 0
 * （否则扫描/保留策略出问题时门禁会「全绿」却什么也没检查）。</p>
 */
class SanctionedStatusWriterContractTest {

    private static final int EXPECTED_MINIMUM = 15;

    @Test
    void everyAnnotatedWriter_mustDeclareReason_andWhitelistMustNotVanish() throws Exception {
        List<String> violations = new ArrayList<>();
        List<String> annotated = new ArrayList<>();

        for (Class<?> type : scanAllClasses()) {
            SanctionedStatusWriter annotation = type.getAnnotation(SanctionedStatusWriter.class);
            if (annotation == null) {
                continue;
            }
            annotated.add(type.getName());
            if (annotation.value().isBlank()) {
                violations.add(type.getName() + " 未写明豁免理由");
            }
        }

        assertThat(violations).isEmpty();
        assertThat(annotated)
                .as("状态写豁免类应覆盖各单据的 ApplyService / CompletionSyncService / ForceCloseService，"
                        + "数量骤降说明注解被误删或扫描失效")
                .hasSizeGreaterThanOrEqualTo(EXPECTED_MINIMUM);
    }

    private List<Class<?>> scanAllClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.leo.erp")) {
            classes.add(Class.forName(candidate.getBeanClassName()));
        }
        assertThat(classes).isNotEmpty();
        return classes;
    }
}
