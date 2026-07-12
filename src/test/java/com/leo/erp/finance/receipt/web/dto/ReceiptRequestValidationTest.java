package com.leo.erp.finance.receipt.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankCustomerName() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "",
                "项目名称",
                null,
                LocalDate.of(2026, 4, 25),
                "银行转账",
                BigDecimal.valueOf(10000),
                "草稿",
                "经办人",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("customerName");
    }

    @Test
    void shouldRejectBlankProjectName() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "客户名称",
                "",
                null,
                LocalDate.of(2026, 4, 25),
                "银行转账",
                BigDecimal.valueOf(10000),
                "草稿",
                "经办人",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("projectName");
    }

    @Test
    void shouldRejectNullReceiptDate() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "客户名称",
                "项目名称",
                null,
                null,
                "银行转账",
                BigDecimal.valueOf(10000),
                "草稿",
                "经办人",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("receiptDate");
    }

    @Test
    void shouldRejectBlankPayType() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "客户名称",
                "项目名称",
                null,
                LocalDate.of(2026, 4, 25),
                "",
                BigDecimal.valueOf(10000),
                "草稿",
                "经办人",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("payType");
    }

    @Test
    void shouldRejectNullAmount() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "客户名称",
                "项目名称",
                null,
                LocalDate.of(2026, 4, 25),
                "银行转账",
                null,
                "草稿",
                "经办人",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("amount");
    }

    @Test
    void shouldRejectNegativeAmount() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "客户名称",
                "项目名称",
                null,
                LocalDate.of(2026, 4, 25),
                "银行转账",
                BigDecimal.valueOf(-10000),
                "草稿",
                "经办人",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("amount");
    }

    @Test
    void shouldRejectBlankStatus() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "客户名称",
                "项目名称",
                null,
                LocalDate.of(2026, 4, 25),
                "银行转账",
                BigDecimal.valueOf(10000),
                "",
                "经办人",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("status");
    }

    @Test
    void shouldRejectBlankOperatorName() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "客户名称",
                "项目名称",
                null,
                LocalDate.of(2026, 4, 25),
                "银行转账",
                BigDecimal.valueOf(10000),
                "草稿",
                "",
                "备注",
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("operatorName");
    }

    @Test
    void shouldRejectInvalidAllocationItem() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "客户名称",
                "项目名称",
                null,
                LocalDate.of(2026, 4, 25),
                "银行转账",
                BigDecimal.valueOf(10000),
                "草稿",
                "经办人",
                "备注",
                List.of(new ReceiptAllocationRequest(
                        null,
                        null,
                        null
                ))
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("items[0].sourceCustomerStatementId");
        assertThat(violations).contains("items[0].allocatedAmount");
    }

    @Test
    void shouldAcceptValidRequest() {
        ReceiptRequest request = new ReceiptRequest(
                "R001",
                "客户名称",
                "项目名称",
                null,
                LocalDate.of(2026, 4, 25),
                "银行转账",
                BigDecimal.valueOf(10000),
                "草稿",
                "经办人",
                "备注",
                List.of()
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
