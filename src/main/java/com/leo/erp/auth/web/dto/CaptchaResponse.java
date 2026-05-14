package com.leo.erp.auth.web.dto;

public record CaptchaResponse(
        String captchaId,
        String captchaImage,
        boolean required
) {
}
