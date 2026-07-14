package com.leo.erp.system.norule.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoRuleRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPass_whenValidRequest() {
        var request = new NoRuleRequest(
                "SALE_ORDER", "销售订单", "销售订单", "SO", "yyyyMMdd", 6, "按年", "SO20240101000001", "正常", "备注"
        );

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFail_whenSettingCodeBlank() {
        var request = new NoRuleRequest(
                "", "销售订单", "销售订单", "SO", "yyyyMMdd", 6, "按年", "SO20240101000001", "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("settingCode"))).isTrue();
    }

    @Test
    void shouldFail_whenSettingNameBlank() {
        var request = new NoRuleRequest(
                "SALE_ORDER", "", "销售订单", "SO", "yyyyMMdd", 6, "按年", "SO20240101000001", "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("settingName"))).isTrue();
    }

    @Test
    void shouldFail_whenSerialLengthLessThan1() {
        var request = new NoRuleRequest(
                "SALE_ORDER", "销售订单", "销售订单", "SO", "yyyyMMdd", 0, "按年", "SO20240101000001", "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("serialLength"))).isTrue();
    }

    @Test
    void shouldFail_whenPrefixBlank() {
        var request = new NoRuleRequest(
                "SALE_ORDER", "销售订单", "销售订单", "", "yyyyMMdd", 6, "按年", "SO20240101000001", "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("prefix"))).isTrue();
    }
}
