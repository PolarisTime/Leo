package com.leo.erp.sales.outbound.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOutboundRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankCustomerName() {
        SalesOutboundRequest request = new SalesOutboundRequest(
                "OB001",
                "SO001",
                "",
                "项目名称",
                "一号库",
                LocalDate.of(2026, 4, 25),
                "草稿",
                "备注",
                List.of(new SalesOutboundItemRequest(
                        null,
                        null,
                        "M001",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "6m",
                        "吨",
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
        SalesOutboundRequest request = new SalesOutboundRequest(
                "OB001",
                "SO001",
                "客户名称",
                "",
                "一号库",
                LocalDate.of(2026, 4, 25),
                "草稿",
                "备注",
                List.of(new SalesOutboundItemRequest(
                        null,
                        null,
                        "M001",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "6m",
                        "吨",
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
    void shouldRejectNullOutboundDate() {
        SalesOutboundRequest request = new SalesOutboundRequest(
                "OB001",
                "SO001",
                "客户名称",
                "项目名称",
                "一号库",
                null,
                "草稿",
                "备注",
                List.of(new SalesOutboundItemRequest(
                        null,
                        null,
                        "M001",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "6m",
                        "吨",
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

        assertThat(violations).contains("outboundDate");
    }

    @Test
    void shouldRejectEmptyItems() {
        SalesOutboundRequest request = new SalesOutboundRequest(
                "OB001",
                "SO001",
                "客户名称",
                "项目名称",
                "一号库",
                LocalDate.of(2026, 4, 25),
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
        SalesOutboundRequest request = new SalesOutboundRequest(
                "OB001",
                "SO001",
                "客户名称",
                "项目名称",
                "一号库",
                LocalDate.of(2026, 4, 25),
                "草稿",
                "备注",
                List.of(new SalesOutboundItemRequest(
                        null,
                        null,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "6m",
                        "",
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
        SalesOutboundRequest request = new SalesOutboundRequest(
                "OB001",
                "SO001",
                "客户名称",
                "项目名称",
                "一号库",
                LocalDate.of(2026, 4, 25),
                "草稿",
                "备注",
                List.of(new SalesOutboundItemRequest(
                        null,
                        null,
                        "M001",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "6m",
                        "吨",
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