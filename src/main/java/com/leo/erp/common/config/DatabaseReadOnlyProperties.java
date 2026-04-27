package com.leo.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "leo.database")
public record DatabaseReadOnlyProperties(boolean readOnly) {
}
