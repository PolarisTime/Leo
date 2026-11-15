package com.leo.erp.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final ApiProblemFactory problemFactory;

    public ApiErrorResponseWriter(ObjectMapper objectMapper, ApiProblemFactory problemFactory) {
        this.objectMapper = objectMapper;
        this.problemFactory = problemFactory;
    }

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      HttpStatus status,
                      ErrorCode errorCode,
                      String message) throws IOException {
        response.setStatus(status.value());
        // 统一使用 application/problem+json（不带 charset），与 GlobalExceptionHandler.failure() 保持一致；
        // JSON 按 RFC 8259 固定 UTF-8，无需在 Content-Type 中重复声明。
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                problemFactory.create(request, status, errorCode, message)
        );
    }
}
