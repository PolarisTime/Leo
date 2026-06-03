package com.leo.erp.master.carrier.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankCarrierCode() {
        CarrierRequest request = new CarrierRequest(
                "",
                "物流方名称",
                "联系人",
                "13800138000",
                "平板车",
                List.of(),
                "按吨",
                "启用",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("carrierCode");
    }

    @Test
    void shouldRejectBlankCarrierName() {
        CarrierRequest request = new CarrierRequest(
                "C001",
                "",
                "联系人",
                "13800138000",
                "平板车",
                List.of(),
                "按吨",
                "启用",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("carrierName");
    }

    @Test
    void shouldRejectBlankStatus() {
        CarrierRequest request = new CarrierRequest(
                "C001",
                "物流方名称",
                "联系人",
                "13800138000",
                "平板车",
                List.of(),
                "按吨",
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
        CarrierRequest request = new CarrierRequest(
                "C001",
                "物流方名称",
                "联系人",
                "13800138000",
                "平板车",
                List.of(),
                "按吨",
                "启用",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptNullOptionalFields() {
        CarrierRequest request = new CarrierRequest(
                "C001",
                "物流方名称",
                null,
                null,
                null,
                null,
                null,
                "启用",
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
