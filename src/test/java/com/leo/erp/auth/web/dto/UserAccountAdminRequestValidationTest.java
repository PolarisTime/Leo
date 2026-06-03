package com.leo.erp.auth.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountAdminRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankLoginName() {
        UserAccountAdminRequest request = new UserAccountAdminRequest(
                "",
                "password123",
                "用户姓名",
                "13800138000",
                1L,
                List.of("admin"),
                List.of(1L),
                "全部数据",
                "权限摘要",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("loginName");
    }

    @Test
    void shouldRejectInvalidLoginNameFormat() {
        UserAccountAdminRequest request = new UserAccountAdminRequest(
                "admin@#$",
                "password123",
                "用户姓名",
                "13800138000",
                1L,
                List.of("admin"),
                List.of(1L),
                "全部数据",
                "权限摘要",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("loginName");
    }

    @Test
    void shouldRejectBlankUserName() {
        UserAccountAdminRequest request = new UserAccountAdminRequest(
                "admin",
                "password123",
                "",
                "13800138000",
                1L,
                List.of("admin"),
                List.of(1L),
                "全部数据",
                "权限摘要",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("userName");
    }

    @Test
    void shouldRejectInvalidMobileFormat() {
        UserAccountAdminRequest request = new UserAccountAdminRequest(
                "admin",
                "password123",
                "用户姓名",
                "01234567890",
                1L,
                List.of("admin"),
                List.of(1L),
                "全部数据",
                "权限摘要",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("mobile");
    }

    @Test
    void shouldRejectInvalidDataScope() {
        UserAccountAdminRequest request = new UserAccountAdminRequest(
                "admin",
                "password123",
                "用户姓名",
                "13800138000",
                1L,
                List.of("admin"),
                List.of(1L),
                "无效范围",
                "权限摘要",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("dataScope");
    }

    @Test
    void shouldRejectInvalidStatus() {
        UserAccountAdminRequest request = new UserAccountAdminRequest(
                "admin",
                "password123",
                "用户姓名",
                "13800138000",
                1L,
                List.of("admin"),
                List.of(1L),
                "全部数据",
                "权限摘要",
                "无效状态",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("status");
    }

    @Test
    void shouldAcceptValidRequest() {
        UserAccountAdminRequest request = new UserAccountAdminRequest(
                "admin",
                "password123",
                "用户姓名",
                "13800138000",
                1L,
                List.of("admin"),
                List.of(1L),
                "全部数据",
                "权限摘要",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptNullOptionalFields() {
        UserAccountAdminRequest request = new UserAccountAdminRequest(
                "admin",
                null,
                "用户姓名",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}