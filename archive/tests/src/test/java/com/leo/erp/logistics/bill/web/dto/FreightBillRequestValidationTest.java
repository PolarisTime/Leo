package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankCarrierName() {
        FreightBillRequest request = new FreightBillRequest(
                "FB001",
                "",
                "京A12345",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 4, 25),
                BigDecimal.valueOf(100.00),
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("carrierName");
    }

    @Test
    void shouldRejectBlankCustomerName() {
        FreightBillRequest request = new FreightBillRequest(
                "FB001",
                "物流方名称",
                "京A12345",
                "",
                "项目名称",
                LocalDate.of(2026, 4, 25),
                BigDecimal.valueOf(100.00),
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("customerName");
    }

    @Test
    void shouldRejectBlankProjectName() {
        FreightBillRequest request = new FreightBillRequest(
                "FB001",
                "物流方名称",
                "京A12345",
                "客户名称",
                "",
                LocalDate.of(2026, 4, 25),
                BigDecimal.valueOf(100.00),
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("projectName");
    }

    @Test
    void shouldRejectNullBillTime() {
        FreightBillRequest request = new FreightBillRequest(
                "FB001",
                "物流方名称",
                "京A12345",
                "客户名称",
                "项目名称",
                null,
                BigDecimal.valueOf(100.00),
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("billTime");
    }

    @Test
    void shouldRejectNullUnitPrice() {
        FreightBillRequest request = new FreightBillRequest(
                "FB001",
                "物流方名称",
                "京A12345",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 4, 25),
                null,
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("unitPrice");
    }

    @Test
    void shouldRejectNegativeUnitPrice() {
        FreightBillRequest request = new FreightBillRequest(
                "FB001",
                "物流方名称",
                "京A12345",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 4, 25),
                BigDecimal.valueOf(-100.00),
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("unitPrice");
    }

    @Test
    void shouldRejectEmptyItems() {
        FreightBillRequest request = new FreightBillRequest(
                "FB001",
                "物流方名称",
                "京A12345",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 4, 25),
                BigDecimal.valueOf(100.00),
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
        FreightBillItemRequest invalidItem = new FreightBillItemRequest(
                null,
                "",
                "",
                "",
                "",
                null,
                "",
                "",
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        FreightBillRequest request = new FreightBillRequest(
                "FB001",
                "物流方名称",
                "京A12345",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 4, 25),
                BigDecimal.valueOf(100.00),
                "草稿",
                "备注",
                List.of(invalidItem)
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).anyMatch(v -> v.startsWith("items[0]."));
    }

    @Test
    void shouldAcceptValidRequest() {
        FreightBillRequest request = new FreightBillRequest(
                "FB001",
                "物流方名称",
                "京A12345",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 4, 25),
                BigDecimal.valueOf(100.00),
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptNullOptionalFields() {
        FreightBillRequest request = new FreightBillRequest(
                null,
                "物流方名称",
                null,
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 4, 25),
                BigDecimal.valueOf(100.00),
                null,
                null,
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    private FreightBillItemRequest validItem() {
        return new FreightBillItemRequest(
                null,
                "SO001",
                1001L,
                null,
                null,
                null,
                "客户名称",
                null,
                "项目名称",
                null,
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
                null,
                "仓库A",
                null,
                null,
                2001L
        );
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}
