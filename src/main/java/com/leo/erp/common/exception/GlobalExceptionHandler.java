package com.leo.erp.common.exception;

import com.leo.erp.common.api.ApiFieldError;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ApiProblemFactory problemFactory;

    public GlobalExceptionHandler(ApiProblemFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return failure(
                request,
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "请求体格式错误，请检查 JSON 格式"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ApiFieldError> errors = fieldErrors(ex.getBindingResult());
        String message = validationMessage(errors, "请求参数校验失败");
        return failure(
                request,
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.VALIDATION_ERROR,
                message,
                errors
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<?> handleBindException(BindException ex, HttpServletRequest request) {
        List<ApiFieldError> errors = fieldErrors(ex.getBindingResult());
        String message = validationMessage(errors, "请求参数绑定失败");
        return failure(
                request,
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.VALIDATION_ERROR,
                message,
                errors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()
                ))
                .toList();
        String message = validationMessage(errors, "请求参数校验失败");
        return failure(
                request,
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.VALIDATION_ERROR,
                message,
                errors
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String parameterName = ex.getName() == null || ex.getName().isBlank() ? "参数" : ex.getName();
        return failure(
                request,
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                parameterName + ": 参数格式错误"
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return failure(
                request,
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                ex.getParameterName() + ": 参数不能为空"
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<?> handleMissingRequestHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        return failure(
                request,
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                ex.getHeaderName() + ": 请求头不能为空"
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<?> handleMissingServletRequestPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request
    ) {
        return failure(
                request,
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                ex.getRequestPartName() + ": 文件不能为空"
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        return failure(
                request,
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED,
                "当前资源不支持 " + ex.getMethod() + " 请求"
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<?> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request
    ) {
        return failure(
                request,
                HttpStatus.NOT_ACCEPTABLE,
                ErrorCode.NOT_ACCEPTABLE,
                "无法生成客户端可接受的响应类型"
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {
        return failure(
                request,
                HttpStatus.PAYLOAD_TOO_LARGE,
                ErrorCode.PAYLOAD_TOO_LARGE,
                "上传内容超过允许的大小"
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        return failure(
                request,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "不支持请求的 Content-Type"
        );
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = resolveStatus(ex.getErrorCode());
        return failure(request, status, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<?> handleOptimisticLockingFailure(
            Exception ex,
            HttpServletRequest request
    ) {
        return failure(
                request,
                HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MODIFICATION,
                ErrorCode.CONCURRENT_MODIFICATION.getMessage()
        );
    }

    @ExceptionHandler({BadCredentialsException.class, JwtException.class})
    public ResponseEntity<?> handleUnauthorized(Exception ex, HttpServletRequest request) {
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "认证失败";
        return failure(request, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return failure(request, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "拒绝访问");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return failure(request, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex, HttpServletRequest request) {
        log.error("系统异常", ex);
        return failure(request, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "系统异常");
    }

    private ResponseEntity<?> failure(HttpServletRequest request,
                                      HttpStatus status,
                                      ErrorCode errorCode,
                                      String message) {
        return failure(request, status, errorCode, message, List.of());
    }

    private ResponseEntity<?> failure(HttpServletRequest request,
                                      HttpStatus status,
                                      ErrorCode errorCode,
                                      String message,
                                      List<ApiFieldError> errors) {
        logClientException(request, errorCode, message);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemFactory.create(request, status, errorCode, message, errors));
    }

    private List<ApiFieldError> fieldErrors(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(error -> new ApiFieldError(
                        error.getField(),
                        error.getCode() == null ? "Invalid" : error.getCode(),
                        error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage()
                ))
                .toList();
    }

    private String validationMessage(List<ApiFieldError> errors, String fallback) {
        if (errors == null || errors.isEmpty()) {
            return fallback;
        }
        return errors.stream()
                .map(error -> error.field() + ": " + error.message())
                .collect(Collectors.joining("; "));
    }

    private HttpStatus resolveStatus(ErrorCode errorCode) {
        if (errorCode == null) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return switch (errorCode) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED, SESSION_EVICTED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case CONCURRENT_MODIFICATION, REFRESH_TOKEN_REUSE_CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case SUCCESS -> HttpStatus.OK;
        };
    }

    private void logClientException(HttpServletRequest request, ErrorCode errorCode, String message) {
        if (request == null) {
            log.warn("请求失败 code={} message={}", codeOf(errorCode), message);
            return;
        }
        log.warn(
                "请求失败 method={} uri={} code={} message={}",
                request.getMethod(),
                request.getRequestURI(),
                codeOf(errorCode),
                message
        );
    }

    private int codeOf(ErrorCode errorCode) {
        return errorCode == null ? ErrorCode.BUSINESS_ERROR.getCode() : errorCode.getCode();
    }
}
