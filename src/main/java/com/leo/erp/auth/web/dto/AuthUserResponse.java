package com.leo.erp.auth.web.dto;

public record AuthUserResponse(
        Long id,
        String loginName,
        String userName
) {
}
