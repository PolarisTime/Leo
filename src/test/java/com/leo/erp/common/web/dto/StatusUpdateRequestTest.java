package com.leo.erp.common.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StatusUpdateRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validRequest_noViolations() {
        StatusUpdateRequest request = new StatusUpdateRequest("ACTIVE");
        Set<ConstraintViolation<StatusUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void blankStatus_violatesNotBlank() {
        StatusUpdateRequest request = new StatusUpdateRequest("");
        Set<ConstraintViolation<StatusUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void nullStatus_violatesNotBlank() {
        StatusUpdateRequest request = new StatusUpdateRequest(null);
        Set<ConstraintViolation<StatusUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void statusExceedsMaxLength_violatesSize() {
        String longStatus = "A".repeat(33);
        StatusUpdateRequest request = new StatusUpdateRequest(longStatus);
        Set<ConstraintViolation<StatusUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void statusAtMaxLength_valid() {
        String maxStatus = "A".repeat(32);
        StatusUpdateRequest request = new StatusUpdateRequest(maxStatus);
        Set<ConstraintViolation<StatusUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void recordAccessors_work() {
        StatusUpdateRequest request = new StatusUpdateRequest("INACTIVE");
        assertThat(request.status()).isEqualTo("INACTIVE");
    }
}
