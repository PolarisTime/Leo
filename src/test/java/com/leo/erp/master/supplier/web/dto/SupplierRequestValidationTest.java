package com.leo.erp.master.supplier.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankSupplierCode() {
        SupplierRequest request = new SupplierRequest(
                "",
                "供应商名称",
                "联系人",
                "13800138000",
                "上海",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("supplierCode");
    }

    @Test
    void shouldRejectBlankSupplierName() {
        SupplierRequest request = new SupplierRequest(
                "S001",
                "",
                "联系人",
                "13800138000",
                "上海",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("supplierName");
    }

    @Test
    void shouldRejectBlankStatus() {
        SupplierRequest request = new SupplierRequest(
                "S001",
                "供应商名称",
                "联系人",
                "13800138000",
                "上海",
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
        SupplierRequest request = new SupplierRequest(
                "S001",
                "供应商名称",
                "联系人",
                "13800138000",
                "上海",
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