package com.leo.erp.auth.web.dto;

public record TotpSetupResponse(
        String qrCodeBase64,
        String secret
) {
}
