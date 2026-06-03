package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillItemRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankSourceNo() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("sourceNo");
    }

    @Test
    void shouldRejectBlankCustomerName() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("customerName");
    }

    @Test
    void shouldRejectBlankProjectName() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("projectName");
    }

    @Test
    void shouldRejectBlankMaterialCode() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("materialCode");
    }

    @Test
    void shouldRejectBlankBrand() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("brand");
    }

    @Test
    void shouldRejectBlankCategory() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("category");
    }

    @Test
    void shouldRejectBlankMaterial() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("material");
    }

    @Test
    void shouldRejectBlankSpec() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("spec");
    }

    @Test
    void shouldRejectNullQuantity() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                null,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("quantity");
    }

    @Test
    void shouldRejectNegativeQuantity() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                -1,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("quantity");
    }

    @Test
    void shouldRejectNullPieceWeightTon() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                null,
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("pieceWeightTon");
    }

    @Test
    void shouldRejectNegativePieceWeightTon() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(-0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("pieceWeightTon");
    }

    @Test
    void shouldRejectNullPiecesPerBundle() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                null,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("piecesPerBundle");
    }

    @Test
    void shouldRejectNegativePiecesPerBundle() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                -1,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("piecesPerBundle");
    }

    @Test
    void shouldAcceptValidRequest() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                "钢材",
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                "6m",
                100,
                "件",
                BigDecimal.valueOf(0.500),
                10,
                "B001",
                BigDecimal.valueOf(50.000),
                "仓库A"
        );

        Set<String> violations = VALIDATOR.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptNullOptionalFields() {
        FreightBillItemRequest item = new FreightBillItemRequest(
                null,
                "SO001",
                "客户名称",
                "项目名称",
                "M001",
                null,
                "品牌A",
                "类别A",
                "钢材",
                "10mm",
                null,
                100,
                null,
                BigDecimal.valueOf(0.500),
                10,
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
