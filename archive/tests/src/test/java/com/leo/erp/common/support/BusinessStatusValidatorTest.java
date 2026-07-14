package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessStatusValidatorTest {

    private static final Set<String> ALLOWED = Set.of("正常", "禁用");

    @Test
    void shouldReturnNormalizedValue() {
        String result = BusinessStatusValidator.normalizeRequired("正常", "状态", ALLOWED);
        assertThat(result).isEqualTo("正常");
    }

    @Test
    void shouldTrimValue() {
        String result = BusinessStatusValidator.normalizeRequired("  正常  ", "状态", ALLOWED);
        assertThat(result).isEqualTo("正常");
    }

    @Test
    void shouldThrowWhenValueIsNull() {
        assertThatThrownBy(() -> BusinessStatusValidator.normalizeRequired(null, "状态", ALLOWED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldThrowWhenValueIsEmpty() {
        assertThatThrownBy(() -> BusinessStatusValidator.normalizeRequired("", "状态", ALLOWED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldThrowWhenValueIsBlank() {
        assertThatThrownBy(() -> BusinessStatusValidator.normalizeRequired("   ", "状态", ALLOWED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldThrowWhenValueNotAllowed() {
        assertThatThrownBy(() -> BusinessStatusValidator.normalizeRequired("删除", "状态", ALLOWED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void shouldUseDefaultWhenValueIsNull() {
        String result = BusinessStatusValidator.normalizeWithDefault(null, "正常", "状态", ALLOWED);
        assertThat(result).isEqualTo("正常");
    }

    @Test
    void shouldUseProvidedValueOverDefault() {
        String result = BusinessStatusValidator.normalizeWithDefault("禁用", "正常", "状态", ALLOWED);
        assertThat(result).isEqualTo("禁用");
    }

    @Test
    void shouldUseDefaultWhenValueIsEmpty() {
        String result = BusinessStatusValidator.normalizeWithDefault("", "正常", "状态", ALLOWED);
        assertThat(result).isEqualTo("正常");
    }
}
