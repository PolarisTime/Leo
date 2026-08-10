package com.leo.erp.statement.service;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StatementSourceCoverageValidator 极端情况测试。
 */
class StatementSourceCoverageValidatorTest {

    @Test
    void shouldPassWhenEffectiveIdsEmpty() {
        assertThatCode(() -> StatementSourceCoverageValidator
                .requireAllEffectiveItems("运费", List.of(), List.of(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenEffectiveIdsNull() {
        assertThatCode(() -> StatementSourceCoverageValidator
                .requireAllEffectiveItems("运费", null, List.of(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenRequestedCoversAllEffective() {
        assertThatCode(() -> StatementSourceCoverageValidator
                .requireAllEffectiveItems("运费", List.of(1L, 2L), List.of(1L, 2L, 3L)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldThrowWhenRequestedMissingEffectiveIds() {
        assertThatThrownBy(() -> StatementSourceCoverageValidator
                .requireAllEffectiveItems("运费", List.of(1L, 2L), List.of(1L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowWhenRequestedNull() {
        assertThatThrownBy(() -> StatementSourceCoverageValidator
                .requireAllEffectiveItems("运费", List.of(1L), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldIgnoreNullIdsDuringComparison() {
        // effective 全部为 null → normalize 过滤后为空 → 不要求（Arrays.asList 允许 null 元素）
        assertThatCode(() -> StatementSourceCoverageValidator
                .requireAllEffectiveItems("运费", Arrays.asList((Long) null), List.of()))
                .doesNotThrowAnyException();
        // 双方均含 null，比较时忽略 null
        assertThatCode(() -> StatementSourceCoverageValidator
                .requireAllEffectiveItems("运费", Arrays.asList(1L, null), Arrays.asList(1L, null)))
                .doesNotThrowAnyException();
    }
}
