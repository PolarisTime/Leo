package com.leo.erp.system.role.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleSettingRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPass_whenValidRequest() {
        var request = new RoleSettingRequest(
                "ADMIN", "管理员", "系统角色", "全部", "管理权限", 10, "正常", "备注"
        );

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFail_whenRoleCodeBlank() {
        var request = new RoleSettingRequest(
                "", "管理员", "系统角色", "全部", null, null, "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("roleCode"))).isTrue();
    }

    @Test
    void shouldFail_whenRoleCodeInvalidFormat() {
        var request = new RoleSettingRequest(
                "ADMIN@#$", "管理员", "系统角色", "全部", null, null, "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("roleCode"))).isTrue();
    }

    @Test
    void shouldFail_whenRoleNameBlank() {
        var request = new RoleSettingRequest(
                "ADMIN", "", "系统角色", "全部", null, null, "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("roleName"))).isTrue();
    }

    @Test
    void shouldFail_whenStatusInvalid() {
        var request = new RoleSettingRequest(
                "ADMIN", "管理员", "系统角色", "全部", null, null, "无效", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("status"))).isTrue();
    }
}
