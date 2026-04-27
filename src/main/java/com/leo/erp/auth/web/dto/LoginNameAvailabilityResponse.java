package com.leo.erp.auth.web.dto;

public record LoginNameAvailabilityResponse(
        boolean available,
        String message
) {
}
