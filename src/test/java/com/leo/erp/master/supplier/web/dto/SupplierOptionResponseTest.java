package com.leo.erp.master.supplier.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierOptionResponseTest {

    @Test
    void shouldCreateRecord() {
        SupplierOptionResponse response = new SupplierOptionResponse(
                1L, "供应商甲", "供应商甲"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.label()).isEqualTo("供应商甲");
        assertThat(response.value()).isEqualTo("供应商甲");
    }

    @Test
    void shouldSupportNullValues() {
        SupplierOptionResponse response = new SupplierOptionResponse(
                null, null, null
        );

        assertThat(response.id()).isNull();
        assertThat(response.label()).isNull();
        assertThat(response.value()).isNull();
    }

    @Test
    void shouldImplementEquals() {
        SupplierOptionResponse r1 = new SupplierOptionResponse(1L, "供应商甲", "供应商甲");
        SupplierOptionResponse r2 = new SupplierOptionResponse(1L, "供应商甲", "供应商甲");

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void shouldImplementHashCode() {
        SupplierOptionResponse r1 = new SupplierOptionResponse(1L, "供应商甲", "供应商甲");
        SupplierOptionResponse r2 = new SupplierOptionResponse(1L, "供应商甲", "供应商甲");

        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
