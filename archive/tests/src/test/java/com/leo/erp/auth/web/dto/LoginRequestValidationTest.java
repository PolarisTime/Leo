package com.leo.erp.auth.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankLoginName() {
        LoginRequest request = new LoginRequest(
                "",
                "password123",
                "captcha-id",
                "1234"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("loginName");
    }

    @Test
    void shouldRejectBlankPassword() {
        LoginRequest request = new LoginRequest(
                "admin",
                "",
                "captcha-id",
                "1234"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("password");
    }

    @Test
    void shouldAcceptValidRequest() {
        LoginRequest request = new LoginRequest(
                "admin",
                "password123",
                "captcha-id",
                "1234"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptNullCaptchaFields() {
        LoginRequest request = new LoginRequest(
                "admin",
                "password123",
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