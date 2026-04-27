package com.leo.erp.purchase.order.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderRequestValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectEmptyItemRow() {
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "PO-20260425-001",
                "供应商甲",
                LocalDate.of(2026, 4, 25),
                "采购员A",
                "草稿",
                null,
                List.of(new PurchaseOrderItemRequest(
                        "",
                        "宝钢",
                        "板材",
                        "Q235",
                        "10mm",
                        "",
                        "吨",
                        "一号库",
                        null,
                        1,
                        "件",
                        BigDecimal.valueOf(1.250),
                        1,
                        BigDecimal.valueOf(1.250),
                        BigDecimal.valueOf(4000),
                        BigDecimal.valueOf(5000)
                ))
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("items[0].materialCode");
    }

    @Test
    void shouldRejectMissingItems() {
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "PO-20260425-002",
                "供应商甲",
                LocalDate.of(2026, 4, 25),
                "采购员A",
                "草稿",
                null,
                List.of()
        );

        Set<String> violations = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("items");
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}
