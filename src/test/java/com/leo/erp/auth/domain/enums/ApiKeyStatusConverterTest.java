package com.leo.erp.auth.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyStatusConverterTest {

    private final ApiKeyStatusConverter converter = new ApiKeyStatusConverter();

    @Test
    void shouldConvertActiveToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(ApiKeyStatus.ACTIVE)).isEqualTo("有效");
    }

    @Test
    void shouldConvertDisabledToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(ApiKeyStatus.DISABLED)).isEqualTo("已禁用");
    }

    @Test
    void shouldConvertNullToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void shouldConvertActiveFromDatabaseColumn() {
        assertThat(converter.convertToEntityAttribute("有效")).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    void shouldConvertDisabledFromDatabaseColumn() {
        assertThat(converter.convertToEntityAttribute("已禁用")).isEqualTo(ApiKeyStatus.DISABLED);
    }

    @Test
    void shouldConvertNullFromDatabaseColumn() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void shouldRoundTripConversion() {
        for (ApiKeyStatus status : ApiKeyStatus.values()) {
            String dbValue = converter.convertToDatabaseColumn(status);
            ApiKeyStatus converted = converter.convertToEntityAttribute(dbValue);
            assertThat(converted).isEqualTo(status);
        }
    }
}
