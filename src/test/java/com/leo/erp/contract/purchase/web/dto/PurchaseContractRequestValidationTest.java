package com.leo.erp.contract.purchase.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseContractRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectBlankSupplierName() {
        PurchaseContractRequest request = new PurchaseContractRequest(
                "PC001",
                "",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "采购方名称",
                "草稿",
                "备注",
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("supplierName");
    }

    @Test
    void shouldRejectNullSignDate() {
        PurchaseContractRequest request = new PurchaseContractRequest(
                "PC001",
                "供应商名称",
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "采购方名称",
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
        PurchaseContractRequest request = new PurchaseContractRequest(
                "PC001",
                "供应商名称",
                LocalDate.of(2026, 1, 1),
                null,
                LocalDate.of(2026, 12, 31),
                "采购方名称",
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
        PurchaseContractRequest request = new PurchaseContractRequest(
                "PC001",
                "供应商名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                null,
                "采购方名称",
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
    void shouldRejectBlankBuyerName() {
        PurchaseContractRequest request = new PurchaseContractRequest(
                "PC001",
                "供应商名称",
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

        assertThat(violations).contains("buyerName");
    }

    @Test
    void shouldRejectEmptyItems() {
        PurchaseContractRequest request = new PurchaseContractRequest(
                "PC001",
                "供应商名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "采购方名称",
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
        PurchaseContractItemRequest invalidItem = new PurchaseContractItemRequest(
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
        PurchaseContractRequest request = new PurchaseContractRequest(
                "PC001",
                "供应商名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "采购方名称",
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
        PurchaseContractRequest request = new PurchaseContractRequest(
                "PC001",
                "供应商名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "采购方名称",
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
        PurchaseContractRequest request = new PurchaseContractRequest(
                null,
                "供应商名称",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "采购方名称",
                null,
                null,
                List.of(validItem())
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    private PurchaseContractItemRequest validItem() {
        return new PurchaseContractItemRequest(
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
