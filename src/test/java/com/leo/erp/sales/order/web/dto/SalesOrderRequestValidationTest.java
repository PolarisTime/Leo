package com.leo.erp.sales.order.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankCustomerName() {
        SalesOrderRequest request = new SalesOrderRequest(
                "SO001",
                null,
                null,
                "C001",
                "",
                1L,
                "项目名称",
                LocalDate.of(2026, 4, 25),
                "销售员",
                "草稿",
                "备注",
                List.of(new SalesOrderItemRequest(
                        "M001",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "6m",
                        "吨",
                        null,
                        null,
                        "一号库",
                        null,
                        10,
                        "件",
                        BigDecimal.valueOf(1.250),
                        1,
                        BigDecimal.valueOf(12.5),
                        BigDecimal.valueOf(4000),
                        BigDecimal.valueOf(50000)
                ))
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("customerName");
    }

    @Test
    void shouldRejectBlankProjectName() {
        SalesOrderRequest request = new SalesOrderRequest(
                "SO001",
                null,
                null,
                "C001",
                "客户名称",
                1L,
                "",
                LocalDate.of(2026, 4, 25),
                "销售员",
                "草稿",
                "备注",
                List.of(new SalesOrderItemRequest(
                        "M001",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "6m",
                        "吨",
                        null,
                        null,
                        "一号库",
                        null,
                        10,
                        "件",
                        BigDecimal.valueOf(1.250),
                        1,
                        BigDecimal.valueOf(12.5),
                        BigDecimal.valueOf(4000),
                        BigDecimal.valueOf(50000)
                ))
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("projectName");
    }

    @Test
    void shouldRejectNullDeliveryDate() {
        SalesOrderRequest request = new SalesOrderRequest(
                "SO001",
                null,
                null,
                "C001",
                "客户名称",
                1L,
                "项目名称",
                null,
                "销售员",
                "草稿",
                "备注",
                List.of(new SalesOrderItemRequest(
                        "M001",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "6m",
                        "吨",
                        null,
                        null,
                        "一号库",
                        null,
                        10,
                        "件",
                        BigDecimal.valueOf(1.250),
                        1,
                        BigDecimal.valueOf(12.5),
                        BigDecimal.valueOf(4000),
                        BigDecimal.valueOf(50000)
                ))
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("deliveryDate");
    }

    @Test
    void shouldRejectBlankSalesName() {
        SalesOrderRequest request = new SalesOrderRequest(
                "SO001",
                null,
                null,
                "C001",
                "客户名称",
                1L,
                "项目名称",
                LocalDate.of(2026, 4, 25),
                "",
                "草稿",
                "备注",
                List.of(new SalesOrderItemRequest(
                        "M001",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "6m",
                        "吨",
                        null,
                        null,
                        "一号库",
                        null,
                        10,
                        "件",
                        BigDecimal.valueOf(1.250),
                        1,
                        BigDecimal.valueOf(12.5),
                        BigDecimal.valueOf(4000),
                        BigDecimal.valueOf(50000)
                ))
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("salesName");
    }

    @Test
    void shouldRejectEmptyItems() {
        SalesOrderRequest request = new SalesOrderRequest(
                "SO001",
                null,
                null,
                "C001",
                "客户名称",
                1L,
                "项目名称",
                LocalDate.of(2026, 4, 25),
                "销售员",
                "草稿",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("items");
    }

    @Test
    void shouldRejectInvalidItem() {
        SalesOrderRequest request = new SalesOrderRequest(
                "SO001",
                null,
                null,
                "C001",
                "客户名称",
                1L,
                "项目名称",
                LocalDate.of(2026, 4, 25),
                "销售员",
                "草稿",
                "备注",
                List.of(new SalesOrderItemRequest(
                        "",
                        "",
                        "",
                        "",
                        "",
                        "6m",
                        "",
                        null,
                        null,
                        "",
                        null,
                        -1,
                        "件",
                        BigDecimal.valueOf(-1.250),
                        -1,
                        BigDecimal.valueOf(12.5),
                        BigDecimal.valueOf(-4000),
                        BigDecimal.valueOf(50000)
                ))
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("items[0].materialCode");
        assertThat(violations).contains("items[0].brand");
        assertThat(violations).contains("items[0].category");
        assertThat(violations).contains("items[0].material");
        assertThat(violations).contains("items[0].spec");
        assertThat(violations).contains("items[0].unit");
        assertThat(violations).contains("items[0].warehouseName");
        assertThat(violations).contains("items[0].quantity");
        assertThat(violations).contains("items[0].pieceWeightTon");
        assertThat(violations).contains("items[0].piecesPerBundle");
        assertThat(violations).contains("items[0].unitPrice");
    }

    @Test
    void shouldAcceptValidRequest() {
        SalesOrderRequest request = new SalesOrderRequest(
                "SO001",
                null,
                null,
                "C001",
                "客户名称",
                1L,
                "项目名称",
                LocalDate.of(2026, 4, 25),
                "销售员",
                "草稿",
                "备注",
                List.of(new SalesOrderItemRequest(
                        "M001",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "6m",
                        "吨",
                        null,
                        null,
                        "一号库",
                        null,
                        10,
                        "件",
                        BigDecimal.valueOf(1.250),
                        1,
                        BigDecimal.valueOf(12.5),
                        BigDecimal.valueOf(4000),
                        BigDecimal.valueOf(50000)
                ))
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