package com.leo.erp.master.material.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialCategoryRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankRequiredFields() {
        MaterialCategoryRequest request = new MaterialCategoryRequest(
                " ",
                "",
                0,
                Boolean.FALSE,
                "正常",
                null
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("categoryCode", "categoryName");
    }

    @Test
    void shouldRejectExceededLengthAndNegativeSortOrder() {
        MaterialCategoryRequest request = new MaterialCategoryRequest(
                "A".repeat(33),
                "B".repeat(65),
                -1,
                Boolean.TRUE,
                "X".repeat(17),
                "C".repeat(256)
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("categoryCode", "categoryName", "sortOrder", "status", "remark");
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}
