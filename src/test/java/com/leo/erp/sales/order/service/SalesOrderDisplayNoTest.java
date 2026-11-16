package com.leo.erp.sales.order.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderDisplayNoTest {

    @Test
    void derivesFixedEightDigitDecimalFromSnowflakeId() {
        assertThat(SalesOrderDisplayNo.derive(377434202078969856L)).isEqualTo("19294322");
        assertThat(SalesOrderDisplayNo.derive(377434202078969857L)).isEqualTo("06955762");
        assertThat(SalesOrderDisplayNo.derive(375318419043516416L)).isEqualTo("88491463");
    }

    @Test
    void keepsLeadingZerosSoResultIsAlwaysEightCharacters() {
        for (long id = 0; id < 1_000; id++) {
            assertThat(SalesOrderDisplayNo.derive(id)).hasSize(8);
        }
    }

    @Test
    void adjacentIdsProduceUnrelatedDisplayNumbers() {
        String first = SalesOrderDisplayNo.derive(377434202078969856L);
        String second = SalesOrderDisplayNo.derive(377434202078969857L);
        assertThat(first).isNotEqualTo(second);
    }
}
