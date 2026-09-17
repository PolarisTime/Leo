package com.leo.erp.sales.contract.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesContractRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void valid_request_hasNoViolations() {
        assertThat(validator.validate(request(new BigDecimal("100.00"), new BigDecimal("1.5")))).isEmpty();
    }

    @Test
    void rejectsNullCustomerAndProject() {
        SalesContractRequest request = new SalesContractRequest(
                null, "合同", null, null, LocalDate.of(2026, 9, 17), null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null);

        Set<String> fields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(fields).contains("customerId", "projectId");
    }

    @Test
    void rejectsNegativeAmountAndTonnage() {
        Set<ConstraintViolation<SalesContractRequest>> violations =
                validator.validate(request(new BigDecimal("-0.01"), new BigDecimal("-0.00000001")));

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("totalAmount", "totalTonnage");
    }

    @Test
    void rejectsNullAmountAndTonnageAndSignDate() {
        SalesContractRequest request = new SalesContractRequest(
                null, "合同", 1L, 2L, null, null, null, null, null, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("signDate", "totalAmount", "totalTonnage");
    }

    @Test
    void rejectsBlankName() {
        SalesContractRequest request = new SalesContractRequest(
                null, "  ", 1L, 2L, LocalDate.of(2026, 9, 17), null, null,
                BigDecimal.ONE, BigDecimal.ONE, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    private SalesContractRequest request(BigDecimal amount, BigDecimal tonnage) {
        return new SalesContractRequest(
                null,
                "年度钢材采购合同",
                1L,
                2L,
                LocalDate.of(2026, 9, 17),
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 12, 31),
                amount,
                tonnage,
                null,
                null
        );
    }
}
