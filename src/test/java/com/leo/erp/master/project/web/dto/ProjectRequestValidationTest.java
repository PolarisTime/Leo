package com.leo.erp.master.project.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankProjectCode() {
        ProjectRequest request = new ProjectRequest(
                "",
                "项目名称",
                "项目简称",
                "项目地址",
                "项目经理",
                "C001",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("projectCode");
    }

    @Test
    void shouldRejectBlankProjectName() {
        ProjectRequest request = new ProjectRequest(
                "P001",
                "",
                "项目简称",
                "项目地址",
                "项目经理",
                "C001",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("projectName");
    }

    @Test
    void shouldRejectBlankCustomerCode() {
        ProjectRequest request = new ProjectRequest(
                "P001",
                "项目名称",
                "项目简称",
                "项目地址",
                "项目经理",
                "",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("customerCode");
    }

    @Test
    void shouldRejectBlankStatus() {
        ProjectRequest request = new ProjectRequest(
                "P001",
                "项目名称",
                "项目简称",
                "项目地址",
                "项目经理",
                "C001",
                "",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("status");
    }

    @Test
    void shouldAcceptValidRequest() {
        ProjectRequest request = new ProjectRequest(
                "P001",
                "项目名称",
                "项目简称",
                "项目地址",
                "项目经理",
                "C001",
                "正常",
                "备注"
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