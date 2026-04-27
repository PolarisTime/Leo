package com.leo.erp.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "leo.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        String secret,
        @Positive long accessExpirationMs,
        @Positive long refreshExpirationMs
) {
}
