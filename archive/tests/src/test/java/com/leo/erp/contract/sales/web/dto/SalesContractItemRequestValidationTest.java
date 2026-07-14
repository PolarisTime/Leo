package com.leo.erp.contract.sales.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesContractItemRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankMaterialCode() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("materialCode");
    }

    @Test
    void shouldRejectBlankBrand() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("brand");
    }

    @Test
    void shouldRejectBlankCategory() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("category");
    }

    @Test
    void shouldRejectBlankMaterial() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("material");
    }

    @Test
    void shouldRejectBlankSpec() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("spec");
    }

    @Test
    void shouldRejectBlankUnit() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("unit");
    }

    @Test
    void shouldRejectNullQuantity() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                null,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("quantity");
    }

    @Test
    void shouldRejectNegativeQuantity() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                -1,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("quantity");
    }

    @Test
    void shouldRejectNullPieceWeightTon() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                null,
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("pieceWeightTon");
    }

    @Test
    void shouldRejectNegativePieceWeightTon() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(-0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("pieceWeightTon");
    }

    @Test
    void shouldRejectNullPiecesPerBundle() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                null,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("piecesPerBundle");
    }

    @Test
    void shouldRejectNegativePiecesPerBundle() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                -1,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("piecesPerBundle");
    }

    @Test
    void shouldRejectNullWeightTon() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                null,
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("weightTon");
    }

    @Test
    void shouldRejectNegativeWeightTon() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(-50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("weightTon");
    }

    @Test
    void shouldRejectNullUnitPrice() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                null,
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("unitPrice");
    }

    @Test
    void shouldRejectNegativeUnitPrice() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(-5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("unitPrice");
    }

    @Test
    void shouldRejectNullAmount() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                null
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("amount");
    }

    @Test
    void shouldRejectNegativeAmount() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(-250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("amount");
    }

    @Test
    void shouldAcceptValidRequest() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                "吨",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptNullOptionalFields() {
        SalesContractItemRequest item = new SalesContractItemRequest(
                null,
                "M001",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                null,
                "吨",
                100,
                null,
                BigDecimal.valueOf(0.500),
                10,
                BigDecimal.valueOf(50.000),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(250000.00)
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
