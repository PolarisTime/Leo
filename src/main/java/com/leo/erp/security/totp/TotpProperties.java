package com.leo.erp.security.totp;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "leo.security.totp")
public record TotpProperties(
        @NotBlank String issuer,
        String encryptionKey
) {
}
