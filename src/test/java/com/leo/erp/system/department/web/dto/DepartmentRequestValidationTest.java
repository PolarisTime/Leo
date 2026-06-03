package com.leo.erp.system.department.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPass_whenValidRequest() {
        var request = new DepartmentRequest(
                "DEPT001", "技术部", null, "张三", "13800138000", 1, "正常", "备注"
        );

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFail_whenDepartmentCodeBlank() {
        var request = new DepartmentRequest(
                "", "技术部", null, null, null, null, "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("departmentCode"))).isTrue();
    }

    @Test
    void shouldFail_whenDepartmentNameBlank() {
        var request = new DepartmentRequest(
                "DEPT001", "", null, null, null, null, "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("departmentName"))).isTrue();
    }

    @Test
    void shouldFail_whenStatusInvalid() {
        var request = new DepartmentRequest(
                "DEPT001", "技术部", null, null, null, null, "无效", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("status"))).isTrue();
    }

    @Test
    void shouldFail_whenSortOrderNegative() {
        var request = new DepartmentRequest(
                "DEPT001", "技术部", null, null, null, -1, "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sortOrder"))).isTrue();
    }
}
