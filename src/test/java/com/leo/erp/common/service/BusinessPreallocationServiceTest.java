package com.leo.erp.common.service;

import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessPreallocationServiceTest {

    private final BusinessPreallocationService service = new BusinessPreallocationService() {
        @Override
        public void assertReservedByPrincipal(String moduleKey, long id, SecurityPrincipal principal) {
        }

        @Override
        public void consume(String moduleKey, long id) {
        }
    };

    @Test
    void shouldReturnFalseForDefaultBusinessNoReservationCheck() {
        boolean reserved = service.isBusinessNoReservedByPrincipal("sales-order", "SO-001", null);

        assertThat(reserved).isFalse();
    }

    @Test
    void shouldNoOpWhenConsumingBusinessNoByDefault() {
        service.consumeBusinessNo("sales-order", "SO-001");
    }
}
