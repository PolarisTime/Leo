package com.leo.erp.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionInvalidatedEventTest {

    @Test
    void shouldCreateRecordWithAllProperties() {
        SessionInvalidatedEvent event = new SessionInvalidatedEvent(100L, "token-123", true);

        assertThat(event.userId()).isEqualTo(100L);
        assertThat(event.sessionTokenId()).isEqualTo("token-123");
        assertThat(event.isLogout()).isTrue();
    }

    @Test
    void shouldCreateRecordForNonLogout() {
        SessionInvalidatedEvent event = new SessionInvalidatedEvent(200L, "token-456", false);

        assertThat(event.userId()).isEqualTo(200L);
        assertThat(event.sessionTokenId()).isEqualTo("token-456");
        assertThat(event.isLogout()).isFalse();
    }

    @Test
    void shouldSupportEquality() {
        SessionInvalidatedEvent event1 = new SessionInvalidatedEvent(100L, "token-123", true);
        SessionInvalidatedEvent event2 = new SessionInvalidatedEvent(100L, "token-123", true);

        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    void shouldSupportToString() {
        SessionInvalidatedEvent event = new SessionInvalidatedEvent(100L, "token-123", true);

        assertThat(event.toString()).contains("100", "token-123", "true");
    }
}
