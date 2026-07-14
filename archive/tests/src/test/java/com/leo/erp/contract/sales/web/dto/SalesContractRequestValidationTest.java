package com.leo.erp.contract.sales.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesContractRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankCustomerName() {
        SalesContractRequest request = new SalesContractRequest(
                "SC001",
                "",
                "项目名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "销售员",
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
        SalesContractRequest request = new SalesContractRequest(
                "SC001",
                "客户名称",
                "",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "销售员",
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
    void shouldRejectNullSignDate() {
        SalesContractRequest request = new SalesContractRequest(
                "SC001",
                "客户名称",
                "项目名称",
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "销售员",
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("signDate");
    }

    @Test
    void shouldRejectNullEffectiveDate() {
        SalesContractRequest request = new SalesContractRequest(
                "SC001",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 1, 1),
                null,
                LocalDate.of(2026, 12, 31),
                "销售员",
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("effectiveDate");
    }

    @Test
    void shouldRejectNullExpireDate() {
        SalesContractRequest request = new SalesContractRequest(
                "SC001",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                null,
                "销售员",
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("expireDate");
    }

    @Test
    void shouldRejectBlankSalesName() {
        SalesContractRequest request = new SalesContractRequest(
                "SC001",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "",
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("salesName");
    }

    @Test
    void shouldRejectEmptyItems() {
        SalesContractRequest request = new SalesContractRequest(
                "SC001",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
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
        SalesContractItemRequest invalidItem = new SalesContractItemRequest(
                null,
                "",
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
                null,
                null
        );
        SalesContractRequest request = new SalesContractRequest(
                "SC001",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "销售员",
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
        SalesContractRequest request = new SalesContractRequest(
                "SC001",
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "销售员",
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
        SalesContractRequest request = new SalesContractRequest(
                null,
                "客户名称",
                "项目名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "销售员",
                null,
                null,
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    private SalesContractItemRequest validItem() {
        return new SalesContractItemRequest(
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
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}
