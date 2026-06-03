package com.leo.erp.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OptionResponseTest {

    @Test
    void shouldCreateRecord() {
        OptionResponse response = new OptionResponse("标签", "value");

        assertThat(response.label()).isEqualTo("标签");
        assertThat(response.value()).isEqualTo("value");
    }

    @Test
    void shouldSupportNullValues() {
        OptionResponse response = new OptionResponse(null, null);

        assertThat(response.label()).isNull();
        assertThat(response.value()).isNull();
    }

    @Test
    void shouldImplementEquals() {
        OptionResponse r1 = new OptionResponse("a", "1");
        OptionResponse r2 = new OptionResponse("a", "1");

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void shouldImplementHashCode() {
        OptionResponse r1 = new OptionResponse("a", "1");
        OptionResponse r2 = new OptionResponse("a", "1");

        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
