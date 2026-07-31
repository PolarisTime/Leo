package com.leo.erp.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                problemFactory.create(request, status, errorCode, message)
        );
    }
}
