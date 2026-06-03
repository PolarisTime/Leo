package com.leo.erp.finance.invoicereceipt.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceReceiptRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankInvoiceNo() {
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("10000.00"),
                new BigDecimal("1300.00"),
                "草稿",
                "张三",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("invoiceNo");
    }

    @Test
    void shouldRejectBlankSupplierName() {
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("10000.00"),
                new BigDecimal("1300.00"),
                "草稿",
                "张三",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("supplierName");
    }

    @Test
    void shouldRejectNullInvoiceDate() {
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                null,
                "增值税专用发票",
                new BigDecimal("10000.00"),
                new BigDecimal("1300.00"),
                "草稿",
                "张三",
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
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "",
                new BigDecimal("10000.00"),
                new BigDecimal("1300.00"),
                "草稿",
                "张三",
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
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                null,
                new BigDecimal("1300.00"),
                "草稿",
                "张三",
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
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("-100.00"),
                new BigDecimal("1300.00"),
                "草稿",
                "张三",
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
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("10000.00"),
                null,
                "草稿",
                "张三",
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
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("10000.00"),
                new BigDecimal("1300.00"),
                "",
                "张三",
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
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("10000.00"),
                new BigDecimal("1300.00"),
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
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("10000.00"),
                new BigDecimal("1300.00"),
                "草稿",
                "张三",
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
        InvoiceReceiptItemRequest invalidItem = new InvoiceReceiptItemRequest(
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
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("10000.00"),
                new BigDecimal("1300.00"),
                "草稿",
                "张三",
                "备注",
                List.of(invalidItem)
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("items[0].sourceNo");
        assertThat(violations).contains("items[0].materialCode");
        assertThat(violations).contains("items[0].quantity");
    }

    @Test
    void shouldAcceptValidRequest() {
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "R001",
                "INV001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 1, 15),
                "增值税专用发票",
                new BigDecimal("10000.00"),
                new BigDecimal("1300.00"),
                "草稿",
                "张三",
                "备注",
                List.of(buildValidItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    private InvoiceReceiptItemRequest buildValidItem() {
        return new InvoiceReceiptItemRequest(
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
                10,
                "件",
                new BigDecimal("0.500"),
                5,
                new BigDecimal("5.000"),
                new BigDecimal("4000.00"),
                new BigDecimal("20000.00")
        );
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}
