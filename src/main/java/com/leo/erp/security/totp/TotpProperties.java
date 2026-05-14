package com.leo.erp.security.totp;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "leo.security.totp")
public class TotpProperties {

    @NotBlank
    private String issuer;
    private String encryptionKey;

    public TotpProperties() {
    }

    public TotpProperties(String issuer, String encryptionKey) {
        this.issuer = issuer;
        this.encryptionKey = encryptionKey;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
