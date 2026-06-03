package com.leo.erp.master.warehouse.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankWarehouseCode() {
        WarehouseRequest request = new WarehouseRequest(
                "",
                "仓库名称",
                "原材料库",
                "联系人",
                "13800138000",
                "仓库地址",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("warehouseCode");
    }

    @Test
    void shouldRejectBlankWarehouseName() {
        WarehouseRequest request = new WarehouseRequest(
                "W001",
                "",
                "原材料库",
                "联系人",
                "13800138000",
                "仓库地址",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("warehouseName");
    }

    @Test
    void shouldRejectBlankWarehouseType() {
        WarehouseRequest request = new WarehouseRequest(
                "W001",
                "仓库名称",
                "",
                "联系人",
                "13800138000",
                "仓库地址",
                "正常",
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("warehouseType");
    }

    @Test
    void shouldRejectBlankStatus() {
        WarehouseRequest request = new WarehouseRequest(
                "W001",
                "仓库名称",
                "原材料库",
                "联系人",
                "13800138000",
                "仓库地址",
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
        WarehouseRequest request = new WarehouseRequest(
                "W001",
                "仓库名称",
                "原材料库",
                "联系人",
                "13800138000",
                "仓库地址",
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