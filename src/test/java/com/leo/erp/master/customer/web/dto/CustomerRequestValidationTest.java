package com.leo.erp.master.customer.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankCustomerCode() {
        CustomerRequest request = new CustomerRequest(
                "",
                "客户名称",
                "联系人",
                "13800138000",
                "上海",
                "月结",
                "项目名称",
                "项目简称",
                "项目地址",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("customerCode");
    }

    @Test
    void shouldRejectBlankCustomerName() {
        CustomerRequest request = new CustomerRequest(
                "C001",
                "",
                "联系人",
                "13800138000",
                "上海",
                "月结",
                "项目名称",
                "项目简称",
                "项目地址",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("customerName");
    }

    @Test
    void shouldRejectBlankProjectName() {
        CustomerRequest request = new CustomerRequest(
                "C001",
                "客户名称",
                "联系人",
                "13800138000",
                "上海",
                "月结",
                "",
                "项目简称",
                "项目地址",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("projectName");
    }

    @Test
    void shouldRejectBlankStatus() {
        CustomerRequest request = new CustomerRequest(
                "C001",
                "客户名称",
                "联系人",
                "13800138000",
                "上海",
                "月结",
                "项目名称",
                "项目简称",
                "项目地址",
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
        CustomerRequest request = new CustomerRequest(
                "C001",
                "客户名称",
                "联系人",
                "13800138000",
                "上海",
                "月结",
                "项目名称",
                "项目简称",
                "项目地址",
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