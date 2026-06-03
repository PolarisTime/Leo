package com.leo.erp.contract.purchase.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseContractItemRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankMaterialCode() {
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
        PurchaseContractItemRequest item = new PurchaseContractItemRequest(
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
