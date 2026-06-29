package com.leo.erp.common.exception;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");

    @Test
    void shouldHandleHttpMessageNotReadable() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("Invalid JSON"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(response.getBody().message()).contains("请求体格式错误");
    }

    @Test
    void shouldHandleMethodArgumentNotValid() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        var bindingResult = new org.springframework.validation.BeanPropertyBindingResult(null, "target");
        bindingResult.addError(new FieldError("target", "name", "名称不能为空"));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValid(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("name: 名称不能为空");
    }

    @Test
    void shouldHandleMethodArgumentNotValidWithBlankMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        var bindingResult = new org.springframework.validation.BeanPropertyBindingResult(null, "target");
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValid(ex, request);

        assertThat(response.getBody().message()).isEqualTo("请求参数校验失败");
    }

    @Test
    void shouldHandleBindException() {
        BindException ex = mock(BindException.class);
        var bindingResult = new org.springframework.validation.BeanPropertyBindingResult(null, "target");
        bindingResult.addError(new FieldError("target", "age", "年龄不合法"));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("age: 年龄不合法");
    }

    @Test
    void shouldHandleBindExceptionWithBlankMessage() {
        BindException ex = mock(BindException.class);
        var bindingResult = new org.springframework.validation.BeanPropertyBindingResult(null, "target");
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(ex, request);

        assertThat(response.getBody().message()).isEqualTo("请求参数绑定失败");
    }

    @Test
    void shouldHandleConstraintViolation() {
        ConstraintViolationException ex = new ConstraintViolationException("参数校验失败", null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("参数校验失败");
    }

    @Test
    void shouldHandleMethodArgumentTypeMismatch() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("id: 参数格式错误");
    }

    @Test
    void shouldHandleMethodArgumentTypeMismatchWithNullName() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentTypeMismatch(ex, request);

        assertThat(response.getBody().message()).isEqualTo("参数: 参数格式错误");
    }

    @Test
    void shouldHandleMissingServletRequestParameter() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("page", "int");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingServletRequestParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("page: 参数不能为空");
    }

    @Test
    void shouldHandleMissingServletRequestPart() {
        MissingServletRequestPartException ex = new MissingServletRequestPartException("file");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingServletRequestPart(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("file: 文件不能为空");
    }

    @Test
    void shouldHandleBusinessException() {
        BusinessException ex = new BusinessException(ErrorCode.VALIDATION_ERROR, "业务校验失败");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("业务校验失败");
    }

    @Test
    void shouldMapBusinessExceptionToCorrectHttpStatus() {
        assertThat(handler.handleBusinessException(new BusinessException(ErrorCode.UNAUTHORIZED, ""), request)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleBusinessException(new BusinessException(ErrorCode.FORBIDDEN, ""), request)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleBusinessException(new BusinessException(ErrorCode.NOT_FOUND, ""), request)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleBusinessException(new BusinessException(ErrorCode.INTERNAL_ERROR, ""), request)
                .getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(handler.handleBusinessException(new BusinessException(ErrorCode.RATE_LIMITED, ""), request)
                .getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(handler.handleBusinessException(new BusinessException(ErrorCode.SESSION_EVICTED, ""), request)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleBusinessException(new BusinessException(ErrorCode.REFRESH_TOKEN_REUSE_CONFLICT, ""), request)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleBusinessException(new BusinessException(ErrorCode.BUSINESS_ERROR, ""), request)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void shouldHandleBadCredentialsException() {
        BadCredentialsException ex = new BadCredentialsException("用户名或密码错误");

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnauthorized(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).contains("用户名或密码错误");
    }

    @Test
    void shouldHandleJwtException() {
        JwtException ex = new JwtException("Token已过期") {};

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnauthorized(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).contains("Token已过期");
    }

    @Test
    void shouldHandleUnauthorizedWithNullMessage() {
        BadCredentialsException ex = new BadCredentialsException(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnauthorized(ex, request);

        assertThat(response.getBody().message()).isEqualTo("认证失败");
    }

    @Test
    void shouldHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("拒绝访问");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("无访问权限");
    }

    @Test
    void shouldHandleNoResourceFound() {
        NoResourceFoundException ex = mock(NoResourceFoundException.class);

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResourceFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("资源不存在");
    }

    @Test
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("未知错误");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("系统异常");
    }

    @Test
    void shouldResolveUnprocessableEntity_whenErrorCodeIsBusinessError() {
        BusinessException ex = new BusinessException(ErrorCode.BUSINESS_ERROR, "test");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
