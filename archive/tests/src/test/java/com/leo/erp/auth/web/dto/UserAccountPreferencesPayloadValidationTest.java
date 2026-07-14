package com.leo.erp.auth.web.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountPreferencesPayloadValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectTooManyPages() {
        Map<String, UserListColumnSettingsPayload> pages = java.util.stream.IntStream.range(0, 201)
                .boxed()
                .collect(Collectors.toMap(
                        index -> "page-" + index,
                        index -> new UserListColumnSettingsPayload(List.of("orderNo"), List.of()),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));

        UserAccountPreferencesPayload payload = new UserAccountPreferencesPayload(pages);

        Set<String> violations = VALIDATOR.validate(payload).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("pages");
    }

    @Test
    void shouldRejectTooManyConfiguredColumns() {
        List<String> orderedKeys = java.util.stream.IntStream.range(0, 201)
                .mapToObj(index -> "col-" + index)
                .toList();
        UserAccountPreferencesPayload payload = new UserAccountPreferencesPayload(Map.of(
                "sales-order",
                new UserListColumnSettingsPayload(orderedKeys, List.of())
        ));

        Set<String> violations = VALIDATOR.validate(payload).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violations).contains("pages[sales-order].orderedKeys");
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}
