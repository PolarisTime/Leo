package com.leo.erp.auth.web.dto;

public record LoginStep1Response(
        boolean requires2fa,
        String tempToken
) implements LoginResponseBody {
}
