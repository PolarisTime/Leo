package com.leo.erp.master.carrier.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                99L,
                "京A12345",
                "张三",
                "13800138000",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
        assertThat(item.vehicleId()).isEqualTo(99L);
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

    @Test
    void shouldRejectNonPositiveVehicleId() {
        VehicleItem item = new VehicleItem(
                0L,
                "京A12345",
                null,
                null,
                null
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).containsExactly("vehicleId");
    }

    @Test
    void shouldAcceptLegacyIdJsonAsVehicleId() throws Exception {
        VehicleItem item = new ObjectMapper().readValue(
                "{\"id\":99,\"plate\":\"京A12345\"}",
                VehicleItem.class
        );

        assertThat(item.vehicleId()).isEqualTo(99L);
        assertThat(item.plate()).isEqualTo("京A12345");
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}
