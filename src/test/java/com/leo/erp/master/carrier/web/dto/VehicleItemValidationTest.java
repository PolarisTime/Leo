package com.leo.erp.master.carrier.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleItemValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldAcceptValidRequest() {
        VehicleItem item = new VehicleItem(
                "京A12345",
                "张三",
                "13800138000",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptAllNullFields() {
        VehicleItem item = new VehicleItem(
                null,
                null,
                null,
                null
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
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
