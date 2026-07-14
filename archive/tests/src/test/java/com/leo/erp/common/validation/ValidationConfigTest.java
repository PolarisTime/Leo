package com.leo.erp.common.validation;

import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationConfigTest {

    private final ValidationConfig config = new ValidationConfig();

    @Test
    void methodValidationPostProcessor_createsBean() {
        MethodValidationPostProcessor processor = config.methodValidationPostProcessor();
        assertThat(processor).isNotNull();
    }

    @Test
    void validator_createsLocalValidatorFactoryBean() {
        LocalValidatorFactoryBean validator = config.validator();
        assertThat(validator).isNotNull();
    }
}
