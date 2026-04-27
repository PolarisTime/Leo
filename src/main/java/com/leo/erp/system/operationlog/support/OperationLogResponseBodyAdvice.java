package com.leo.erp.system.operationlog.support;

import com.leo.erp.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class OperationLogResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    static final String RESPONSE_BODY_ATTRIBUTE = OperationLogResponseBodyAdvice.class.getName() + ".responseBody";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }

        HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
        if (body instanceof ResponseEntity<?> responseEntity && responseEntity.getBody() instanceof ApiResponse<?>) {
            httpServletRequest.setAttribute(RESPONSE_BODY_ATTRIBUTE, responseEntity.getBody());
            return body;
        }
        if (body instanceof ApiResponse<?>) {
            httpServletRequest.setAttribute(RESPONSE_BODY_ATTRIBUTE, body);
        }
        return body;
    }
}
