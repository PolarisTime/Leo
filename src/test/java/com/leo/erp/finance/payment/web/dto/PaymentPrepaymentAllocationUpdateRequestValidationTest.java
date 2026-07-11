package com.leo.erp.finance.payment.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPrepaymentAllocationUpdateRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectNullItems() {
        var violations = VALIDATOR.validate(new PaymentPrepaymentAllocationUpdateRequest(null));

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("items");
    }

    @Test
    void shouldCascadeValidationToAllocationItems() {
        var violations = VALIDATOR.validate(new PaymentPrepaymentAllocationUpdateRequest(List.of(
                new PaymentAllocationRequest(null, null, null)
        )));

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "items[0].sourceStatementId",
                        "items[0].allocatedAmount"
                );
    }

    @Test
    void shouldAllowEmptyItemsForClearingAllocations() {
        assertThat(VALIDATOR.validate(new PaymentPrepaymentAllocationUpdateRequest(List.of())))
                .isEmpty();
    }

    private static Validator validator() {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.afterPropertiesSet();
        return factoryBean;
    }
}
