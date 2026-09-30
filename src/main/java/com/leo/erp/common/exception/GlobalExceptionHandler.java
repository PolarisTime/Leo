package com.leo.erp.common.exception;

import com.leo.erp.common.api.ApiFieldError;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.error.ServiceUnavailableException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
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

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 连接获取失败响应体的统一文案。
     * <p>刻意不透出异常原文：Hikari 的失败信息包含数据源名、连接池 total/active/idle 等内部拓扑细节，
     * 只进日志（供排障），不进响应体。</p>
     */
    private static final String CONNECTION_UNAVAILABLE_DETAIL = "数据库连接池暂不可用，请稍后重试";

    /**
     * 连接获取失败时 {@code Retry-After} 的默认秒数。
     *
     * <p>取 5 秒的理由：</p>
     * <ul>
     *   <li>与 {@code JwtAuthenticationFilter} 依赖不可用分支既有的 {@code Retry-After: 5} 保持同一退避约定，
     *       客户端只需实现一套重试策略；</li>
     *   <li>Hikari {@code connection-timeout} 默认 3000ms——请求本身已经阻塞了 3 秒才失败，
     *       再等 5 秒才重试，能给连接池至少一个获取窗口去释放被占用的连接；</li>
     *   <li>更短（如 1s）会在池耗尽期间形成重试风暴、把负载进一步放大；更长则无谓延长可用性损失。</li>
     * </ul>
     */
    private static final int CONNECTION_RETRY_AFTER_SECONDS = 5;

    /**
     * 429（{@link ErrorCode#TOO_MANY_REQUESTS}）响应的 {@code Retry-After} 默认秒数。
     *
     * <p>取 1 秒的理由：429 的来源是**有界并发闸门**（导出闸门 acquire-timeout 500ms、
     * 额度按请求粒度快速释放），被拒请求等 1 秒大概率已有额度——这与连接池耗尽（503）的
     * 5 秒退避语义不同，不应混用；限流过滤器（RateLimitFilter）按窗口精确推导的
     * {@code Retry-After} 在过滤器内直写，不会经过本分支，两者的值互不干扰。</p>
     *
     * <p>背景（2026-09-30 生产量级复测新发现）：module-exports 的 OpenAPI 注解与
     * {@code ExportConcurrencyGuard} Javadoc 均承诺 429 携带 {@code Retry-After}，
     * 但实现走的是不带 header 的失败分支——契约与实现不一致，实测抓包确认缺失。</p>
     */
    private static final int TOO_MANY_REQUESTS_RETRY_AFTER_SECONDS = 1;

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
        if (ex.getErrorCode() == ErrorCode.TOO_MANY_REQUESTS) {
            // 429 必须带 Retry-After（module-exports OpenAPI 注解与 ExportConcurrencyGuard
            // Javadoc 均已承诺）；否则客户端只能盲重试。取值理由见常量注释。
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(TOO_MANY_REQUESTS_RETRY_AFTER_SECONDS));
            return failure(request, status, ex.getErrorCode(), ex.getMessage(), List.of(), headers);
        }
        return failure(request, status, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * 乐观锁冲突映射。
     * <p>报价资源使用资源版本前置条件语义(412 PRECONDITION_FAILED); 其它模块沿用既有的并发冲突语义
     * (409 CONCURRENT_MODIFICATION), 避免全局改变历史调用方契约。</p>
     */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<?> handleOptimisticLockingFailure(
            Exception ex,
            HttpServletRequest request
    ) {
        if (isQuoteResource(request)) {
            return failure(
                    request,
                    HttpStatus.PRECONDITION_FAILED,
                    ErrorCode.PRECONDITION_FAILED,
                    ErrorCode.PRECONDITION_FAILED.getMessage()
            );
        }
        return failure(
                request,
                HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MODIFICATION,
                ErrorCode.CONCURRENT_MODIFICATION.getMessage()
        );
    }

    /**
     * 悲观锁获取失败映射。
     * <p>行级锁等待超时/取消(如 PostgreSQL 55P03 lock_not_available)或死锁时,
     * 统一按并发冲突 409(CONCURRENT_MODIFICATION)返回, 避免长时间阻塞后冒泡为 500。</p>
     */
    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<?> handlePessimisticLockingFailure(
            PessimisticLockingFailureException ex,
            HttpServletRequest request
    ) {
        log.warn("获取数据库锁失败: {}", ex.getMessage(), ex);
        return failure(
                request,
                HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MODIFICATION,
                ErrorCode.CONCURRENT_MODIFICATION.getMessage()
        );
    }

    private boolean isQuoteResource(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri.contains("/quote-sheets") || uri.contains("/quote-project-configs");
    }

    @ExceptionHandler({BadCredentialsException.class, JwtException.class})
    public ResponseEntity<?> handleUnauthorized(Exception ex, HttpServletRequest request) {
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "认证失败";
        return failure(request, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, message);
    }

    /**
     * 依赖服务不可用（如 Redis 抖动）→ 503 + code 5030。
     *
     * <p>为什么必须单独处理：实测 Redis 不可用时，认证链路抛出的
     * {@code RedisConnectionFailureException} 会穿透到通用兜底分支变成 500/5000，
     * 与代码缺陷同码。监控无法区分「依赖挂了」与「代码炸了」，
     * 客户端也无从判断该重试还是该报障。</p>
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<?> handleServiceUnavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : ErrorCode.SERVICE_UNAVAILABLE.getMessage();
        log.warn("依赖服务不可用: uri={} message={}", request == null ? null : request.getRequestURI(), message);
        return failure(request, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE, message);
    }

    /**
     * 数据库连接获取失败（连接池耗尽、数据库不可达）→ 503 + code 5030 + {@code Retry-After}。
     *
     * <p><b>为什么是 503 而不是 500：</b>连接获取失败是服务端依赖（数据库连接池）的容量/可用性问题，
     * 与本次请求的代码正确性无关——同一段代码稍后重试往往就能成功。HTTP 语义里 503 + Retry-After
     * 正是「暂时不可用，稍后重试」；若落进兜底的 5000 系统异常，监控将无法区分「依赖挂了」与
     * 「代码炸了」（2026-09-29 压测建议第 2 条：20 VU 同账号登录时连接池耗尽被记成 7 次 500，
     * 拖垮 06-login 成功率阈值，且把告警指向了错误的方向）。</p>
     *
     * <p><b>为什么与 {@link ServiceUnavailableException} 共用 code 5030：</b>两者对外语义完全一致——
     * 「依赖服务暂不可用，稍后重试可能成功」，区别只在来源：{@link ServiceUnavailableException} 是
     * 领域代码显式判断依赖不可用后抛出（Redis/幂等依赖，bc0041ed 引入），本分支则是 Spring 把 DAO
     * 连接获取失败翻译成 DataAccessException 之后被动产生的数据库依赖故障。code 描述的是对外语义
     * 而非异常类名，复用既有 {@link ErrorCode#SERVICE_UNAVAILABLE} 可以让监控与客户端把所有
     * 「基础设施不可用」聚合为同一类故障，而不是为每种依赖各造一个错误码。</p>
     *
     * <p><b>为什么不把所有 DataAccessException 一刀切：</b>DataAccessException 家族里混着语义完全
     * 不同的分支——{@link DataIntegrityViolationException} 是请求数据本身的问题（唯一键/外键/字段
     * 超长，仍须 409/422），乐观锁与 {@link PessimisticLockingFailureException} 是并发冲突（409）。
     * 尤其不能按父类 {@code TransientDataAccessException} 拦截：悲观锁、死锁同属 transient 家族，
     * 一刀切会把既有 409 契约改坏。因此这里只精确圈定「连接获取/资源失败族」。</p>
     *
     * <p><b>异常族的选择（Hikari {@code SQLTransientConnectionException} 经翻译后的两种形态）：</b></p>
     * <ul>
     *   <li>{@link DataAccessResourceFailureException}：JPA/Hibernate 路径（压测日志实测形态
     *       {@code Unable to acquire JDBC Connection [...]}）以及 SQLState 类 08（connection
     *       exception）的翻译结果；同族子类 {@code org.springframework.jdbc.CannotGetJdbcConnectionException}
     *       与 {@code RedisConnectionFailureException} 也由本处理器覆盖；</li>
     *   <li>{@link TransientDataAccessResourceException}：Spring {@code SQLExceptionSubclassTranslator}
     *       按异常子类把 {@code SQLTransientConnectionException} 直接翻译出来的瞬态形态；</li>
     *   <li>{@link SQLTransientConnectionException}：未经任何翻译直接冒泡时的保险。</li>
     * </ul>
     *
     * <p>注：Spring 并不存在 {@code org.springframework.dao.CannotAcquireConnectionException}
     * （已逐一核对本项目实际依赖的 spring-tx 5.x/6.x 全部版本），瞬态形态即
     * {@link TransientDataAccessResourceException}，故按后者覆盖。</p>
     */
    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            TransientDataAccessResourceException.class,
            SQLTransientConnectionException.class
    })
    public ResponseEntity<?> handleConnectionUnavailable(Exception ex, HttpServletRequest request) {
        // 日志保留异常原文与堆栈：Hikari 的 message 里带 total/active/idle/waiting 连接池状态，
        // 是定位「谁占着连接」的关键证据；响应体则只回统一文案，不泄露内部细节。
        log.warn(
                "数据库连接获取失败（依赖不可用）: uri={} message={}",
                request == null ? null : request.getRequestURI(),
                ex.getMessage(),
                ex
        );
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.set(HttpHeaders.RETRY_AFTER, String.valueOf(CONNECTION_RETRY_AFTER_SECONDS));
        return failure(
                request,
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SERVICE_UNAVAILABLE,
                CONNECTION_UNAVAILABLE_DETAIL,
                List.of(),
                retryAfter
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return failure(request, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "拒绝访问");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return failure(request, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "资源不存在");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null ? "" : String.valueOf(cause.getMessage());
        String sqlState = cause instanceof SQLException sqlException ? sqlException.getSQLState() : null;
        if (isUniqueViolation(sqlState, message)) {
            return failure(
                    request,
                    HttpStatus.CONFLICT,
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "数据已存在或与现有记录冲突"
            );
        }
        if (isForeignKeyViolation(sqlState, message)) {
            log.warn("外键引用冲突: {}", message);
            return failure(
                    request,
                    HttpStatus.CONFLICT,
                    ErrorCode.BUSINESS_ERROR,
                    "数据被其他单据引用，无法删除或修改"
            );
        }
        log.warn("数据完整性校验失败: {}", message);
        return failure(
                request,
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.VALIDATION_ERROR,
                "字段长度或数值超出允许范围"
        );
    }

    private boolean isUniqueViolation(String sqlState, String message) {
        return "23505".equals(sqlState)
                || message.contains("duplicate key")
                || message.contains("unique constraint");
    }

    private boolean isForeignKeyViolation(String sqlState, String message) {
        return "23503".equals(sqlState)
                || "23001".equals(sqlState)
                || message.contains("violates foreign key constraint")
                || message.contains("violates RESTRICT setting")
                || message.contains("still referenced from table");
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
        return failure(request, status, errorCode, message, errors, new HttpHeaders());
    }

    /** 带额外响应头（如 {@code Retry-After}）的失败响应，body 仍统一走 {@link ApiProblemFactory}。 */
    private ResponseEntity<?> failure(HttpServletRequest request,
                                      HttpStatus status,
                                      ErrorCode errorCode,
                                      String message,
                                      List<ApiFieldError> errors,
                                      HttpHeaders headers) {
        logClientException(request, errorCode, message);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (headers != null && !headers.isEmpty()) {
            builder.headers(headers);
        }
        return builder.body(problemFactory.create(request, status, errorCode, message, errors));
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
            case VALIDATION_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNAUTHORIZED, SESSION_EVICTED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case CONCURRENT_MODIFICATION, REFRESH_TOKEN_REUSE_CONFLICT, IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
            case BUSINESS_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
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
