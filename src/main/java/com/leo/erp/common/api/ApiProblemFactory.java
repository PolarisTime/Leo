package com.leo.erp.common.api;

import com.leo.erp.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ApiProblemFactory {

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ZoneId zoneId;

    public ApiProblemFactory(@Value("${leo.timezone:Asia/Shanghai}") String timezone) {
        this.zoneId = ZoneId.of(timezone);
    }

    public ProblemDetail create(HttpServletRequest request,
                                HttpStatus status,
                                ErrorCode errorCode,
                                String detail) {
        return create(request, status, errorCode, detail, List.of());
    }

    public ProblemDetail create(HttpServletRequest request,
                                HttpStatus status,
                                ErrorCode errorCode,
                                String detail,
                                List<ApiFieldError> errors) {
        ErrorCode resolvedErrorCode = errorCode == null ? ErrorCode.INTERNAL_ERROR : errorCode;
        String resolvedDetail = detail == null || detail.isBlank() ? resolvedErrorCode.getMessage() : detail;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, resolvedDetail);
        problem.setType(URI.create("urn:leo:problem:" + problemType(resolvedErrorCode)));
        problem.setTitle(resolvedErrorCode.getMessage());
        if (request != null && request.getRequestURI() != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("code", resolvedErrorCode.getCode());
        problem.setProperty("timestamp", OffsetDateTime.now(zoneId).format(TIMESTAMP_FORMATTER));

        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        if (traceId != null && !traceId.isBlank()) {
            problem.setProperty("traceId", traceId);
        }
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    private String problemType(ErrorCode errorCode) {
        return switch (errorCode) {
            case SUCCESS -> "success";
            case VALIDATION_ERROR -> "validation-error";
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            case NOT_FOUND -> "not-found";
            case METHOD_NOT_ALLOWED -> "method-not-allowed";
            case NOT_ACCEPTABLE -> "not-acceptable";
            case PAYLOAD_TOO_LARGE -> "payload-too-large";
            case UNSUPPORTED_MEDIA_TYPE -> "unsupported-media-type";
            case BUSINESS_ERROR -> "business-error";
            case SESSION_EVICTED -> "session-evicted";
            case CONCURRENT_MODIFICATION -> "concurrent-modification";
            case REFRESH_TOKEN_REUSE_CONFLICT -> "refresh-token-reuse-conflict";
            case INTERNAL_ERROR -> "internal-error";
        };
    }
}
