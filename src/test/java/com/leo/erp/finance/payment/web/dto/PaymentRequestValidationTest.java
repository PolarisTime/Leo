package com.leo.erp.finance.payment.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankBusinessType() {
        PaymentRequest request = new PaymentRequest(
                "P001",
                "",
                "供应商名称",
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

        assertThat(violations).contains("businessType");
    }

    @Test
    void shouldRejectBlankCounterpartyName() {
        PaymentRequest request = new PaymentRequest(
                "P001",
                "采购付款",
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

        assertThat(violations).contains("counterpartyName");
    }

    @Test
    void shouldRejectNullPaymentDate() {
        PaymentRequest request = new PaymentRequest(
                "P001",
                "采购付款",
                "供应商名称",
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

        assertThat(violations).contains("paymentDate");
    }

    @Test
    void shouldRejectBlankPayType() {
        PaymentRequest request = new PaymentRequest(
                "P001",
                "采购付款",
                "供应商名称",
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
        PaymentRequest request = new PaymentRequest(
                "P001",
                "采购付款",
                "供应商名称",
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
        PaymentRequest request = new PaymentRequest(
                "P001",
                "采购付款",
                "供应商名称",
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
        PaymentRequest request = new PaymentRequest(
                "P001",
                "采购付款",
                "供应商名称",
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
        PaymentRequest request = new PaymentRequest(
                "P001",
                "采购付款",
                "供应商名称",
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
        PaymentRequest request = new PaymentRequest(
                "P001",
                "采购付款",
                "供应商名称",
                null,
                LocalDate.of(2026, 4, 25),
                "银行转账",
                BigDecimal.valueOf(10000),
                "草稿",
                "经办人",
                "备注",
                List.of(new PaymentAllocationRequest(
                        null,
                        null,
                        null
                ))
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("items[0].sourceStatementId");
        assertThat(violations).contains("items[0].allocatedAmount");
    }

    @Test
    void shouldAcceptValidRequest() {
        PaymentRequest request = new PaymentRequest(
                "P001",
                "采购付款",
                "供应商名称",
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