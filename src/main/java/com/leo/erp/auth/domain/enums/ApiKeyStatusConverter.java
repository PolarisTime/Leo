package com.leo.erp.auth.domain.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ApiKeyStatusConverter implements AttributeConverter<ApiKeyStatus, String> {

    @Override
    public String convertToDatabaseColumn(ApiKeyStatus attribute) {
        return attribute == null ? null : attribute.displayName();
    }

    @Override
    public ApiKeyStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ApiKeyStatus.fromDisplayName(dbData);
    }
}
