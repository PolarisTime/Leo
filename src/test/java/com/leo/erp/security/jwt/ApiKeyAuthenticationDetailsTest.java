package com.leo.erp.security.jwt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthenticationDetailsTest {

    @Test
    void shouldCreateRecord() {
        ApiKeyAuthenticationDetails details = new ApiKeyAuthenticationDetails(
                "delegate",
                List.of("resource1", "resource2"),
                List.of("read", "create")
        );

        assertThat(details.delegate()).isEqualTo("delegate");
        assertThat(details.allowedResources()).containsExactly("resource1", "resource2");
        assertThat(details.allowedActions()).containsExactly("read", "create");
    }

    @Test
    void shouldSupportEmptyLists() {
        ApiKeyAuthenticationDetails details = new ApiKeyAuthenticationDetails(
                null, List.of(), List.of()
        );

        assertThat(details.delegate()).isNull();
        assertThat(details.allowedResources()).isEmpty();
        assertThat(details.allowedActions()).isEmpty();
    }

    @Test
    void shouldSupportNullDelegate() {
        ApiKeyAuthenticationDetails details = new ApiKeyAuthenticationDetails(
                null, List.of("res"), List.of("act")
        );

        assertThat(details.delegate()).isNull();
    }

    @Test
    void shouldImplementEquals() {
        ApiKeyAuthenticationDetails d1 = new ApiKeyAuthenticationDetails("a", List.of("r"), List.of("a"));
        ApiKeyAuthenticationDetails d2 = new ApiKeyAuthenticationDetails("a", List.of("r"), List.of("a"));

        assertThat(d1).isEqualTo(d2);
    }

    @Test
    void shouldImplementHashCode() {
        ApiKeyAuthenticationDetails d1 = new ApiKeyAuthenticationDetails("a", List.of("r"), List.of("a"));
        ApiKeyAuthenticationDetails d2 = new ApiKeyAuthenticationDetails("a", List.of("r"), List.of("a"));

        assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
    }
}
