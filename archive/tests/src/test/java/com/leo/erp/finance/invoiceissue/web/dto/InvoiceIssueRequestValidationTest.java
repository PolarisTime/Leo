package com.leo.erp.finance.invoiceissue.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceIssueRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankInvoiceNo() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("invoiceNo");
    }

    @Test
    void shouldRejectBlankCustomerName() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("customerName");
    }

    @Test
    void shouldRejectBlankProjectName() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("projectName");
    }

    @Test
    void shouldRejectNullInvoiceDate() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                null,
                "增值税专用发票",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("invoiceDate");
    }

    @Test
    void shouldRejectBlankInvoiceType() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("invoiceType");
    }

    @Test
    void shouldRejectNullAmount() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                null,
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("amount");
    }

    @Test
    void shouldRejectNegativeAmount() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("-100.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("amount");
    }

    @Test
    void shouldRejectNullTaxAmount() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("50000.00"),
                null,
                "草稿",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("taxAmount");
    }

    @Test
    void shouldRejectBlankStatus() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("status");
    }

    @Test
    void shouldRejectBlankOperatorName() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("operatorName");
    }

    @Test
    void shouldRejectEmptyItems() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("items");
    }

    @Test
    void shouldRejectInvalidItem() {
        InvoiceIssueItemRequest invalidItem = new InvoiceIssueItemRequest(
                "",
                null,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                null,
                "",
                null,
                null,
                null,
                null,
                null
        );
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of(invalidItem)
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("items[0].sourceNo");
        assertThat(violations).contains("items[0].sourceSalesOrderItemId");
        assertThat(violations).contains("items[0].materialCode");
        assertThat(violations).contains("items[0].quantity");
    }

    @Test
    void shouldAcceptValidRequest() {
        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "I001",
                "INV001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("50000.00"),
                new BigDecimal("6500.00"),
                "草稿",
                "李四",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    private InvoiceIssueItemRequest buildValidItem() {
        return new InvoiceIssueItemRequest(
                "S001",
                100L,
                "M001",
                "品牌A",
                "钢材",
                "Q235",
                "10mm",
                "6m",
                "吨",
                "仓库A",
                "B001",
                20,
                "件",
                new BigDecimal("0.500"),
                5,
                new BigDecimal("10.000"),
                new BigDecimal("5000.00"),
                new BigDecimal("50000.00")
        );
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}
