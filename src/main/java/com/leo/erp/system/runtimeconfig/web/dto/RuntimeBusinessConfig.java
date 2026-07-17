package com.leo.erp.system.runtimeconfig.web.dto;

import java.math.BigDecimal;

public record RuntimeBusinessConfig(
        BigDecimal defaultTaxRate,
        RuntimeStatementConfig statement
) {
}
