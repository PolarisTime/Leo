package com.leo.erp.master.material.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankMaterialCode() {
        MaterialRequest request = new MaterialRequest(
                "",
                "宝钢",
                "Q235",
                "板材",
                "10mm",
                "6m",
                "吨",
                "件",
                BigDecimal.valueOf(1.250),
                1,
                BigDecimal.valueOf(4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("materialCode");
    }

    @Test
    void shouldRejectBlankBrand() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "",
                "Q235",
                "板材",
                "10mm",
                "6m",
                "吨",
                "件",
                BigDecimal.valueOf(1.250),
                1,
                BigDecimal.valueOf(4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("brand");
    }

    @Test
    void shouldRejectBlankMaterial() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "宝钢",
                "",
                "板材",
                "10mm",
                "6m",
                "吨",
                "件",
                BigDecimal.valueOf(1.250),
                1,
                BigDecimal.valueOf(4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("material");
    }

    @Test
    void shouldRejectBlankCategory() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "宝钢",
                "Q235",
                "",
                "10mm",
                "6m",
                "吨",
                "件",
                BigDecimal.valueOf(1.250),
                1,
                BigDecimal.valueOf(4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("category");
    }

    @Test
    void shouldRejectBlankSpec() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "宝钢",
                "Q235",
                "板材",
                "",
                "6m",
                "吨",
                "件",
                BigDecimal.valueOf(1.250),
                1,
                BigDecimal.valueOf(4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("spec");
    }

    @Test
    void shouldRejectBlankUnit() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "宝钢",
                "Q235",
                "板材",
                "10mm",
                "6m",
                "",
                "件",
                BigDecimal.valueOf(1.250),
                1,
                BigDecimal.valueOf(4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("unit");
    }

    @Test
    void shouldRejectNullPieceWeightTon() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "宝钢",
                "Q235",
                "板材",
                "10mm",
                "6m",
                "吨",
                "件",
                null,
                1,
                BigDecimal.valueOf(4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("pieceWeightTon");
    }

    @Test
    void shouldRejectNegativePieceWeightTon() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "宝钢",
                "Q235",
                "板材",
                "10mm",
                "6m",
                "吨",
                "件",
                BigDecimal.valueOf(-1.250),
                1,
                BigDecimal.valueOf(4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("pieceWeightTon");
    }

    @Test
    void shouldRejectNegativePiecesPerBundle() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "宝钢",
                "Q235",
                "板材",
                "10mm",
                "6m",
                "吨",
                "件",
                BigDecimal.valueOf(1.250),
                -1,
                BigDecimal.valueOf(4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("piecesPerBundle");
    }

    @Test
    void shouldRejectNegativeUnitPrice() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "宝钢",
                "Q235",
                "板材",
                "10mm",
                "6m",
                "吨",
                "件",
                BigDecimal.valueOf(1.250),
                1,
                BigDecimal.valueOf(-4000),
                false,
                "备注"
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("unitPrice");
    }

    @Test
    void shouldAcceptValidRequest() {
        MaterialRequest request = new MaterialRequest(
                "M001",
                "宝钢",
                "Q235",
                "板材",
                "10mm",
                "6m",
                "吨",
                "件",
                BigDecimal.valueOf(1.250),
                1,
                BigDecimal.valueOf(4000),
                false,
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