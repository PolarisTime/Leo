package com.leo.erp.auth.web.dto;

public record ApiKeyUserOptionResponse(
        Long id,
        String loginName,
        String userName,
        String mobile
) {
}
