package com.leo.erp.common.exception;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.error.ServiceUnavailableException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据库连接获取失败的 HTTP 映射契约：
 * 连接池耗尽/数据库不可达必须返回 503 + code 5030 + {@code Retry-After}，
 * 而 409/404/422/5000 等既有映射一条都不能变。
 *
 * <p>背景见 2026-09-29 压测建议第 2 条：连接池耗尽曾被兜底成 500/5000，
 * 监控无法区分「依赖挂了」与「代码炸了」。</p>
 */
class GlobalExceptionHandlerConnectionFailureTest {

    /** 压测报告中的真实 Hikari 连接池耗尽文案（total=20 即 application.yml 的 maximum-pool-size）。 */
    private static final String HIKARI_POOL_EXHAUSTED =
            "HikariPool-1 - Connection is not available, request timed out after 3000ms "
                    + "(total=20, active=20, idle=0, waiting=0)";

    private static final String PROBE_URI = "/api/v2.0/login";

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai"));

    private final FailureProbeController controller = new FailureProbeController();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(handler)
                .build();
    }

    @AfterEach
    void clearTraceId() {
        MDC.remove("traceId");
    }

    // ------------------------------------------------------------------
    // 新增映射：连接获取失败 → 503
    // ------------------------------------------------------------------

    /** 压测实测形态：Hibernate/JPA 翻译出的 DataAccessResourceFailureException。 */
    @Test
    void dataAccessResourceFailure_mapsTo503WithRetryAfterAndCompleteProblemDetail() {
        MDC.put("traceId", "trace-conn-503");

        DataAccessResourceFailureException ex = new DataAccessResourceFailureException(
                "Unable to acquire JDBC Connection",
                new SQLTransientConnectionException(HIKARI_POOL_EXHAUSTED));

        ResponseEntity<?> response = handler.handleConnectionUnavailable(ex, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");

        ProblemDetail problem = problemOf(response);
        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getType().toString()).isEqualTo("urn:leo:problem:service-unavailable");
        assertThat(problem.getTitle()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getMessage());
        // 响应体只给统一文案，不透出连接池内部细节（原文只进日志）
        assertThat(problem.getDetail()).contains("连接池").doesNotContain("HikariPool");
        assertThat(problem.getInstance().toString()).isEqualTo(PROBE_URI);
        assertThat(problem.getProperties().get("code")).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(problem.getProperties().get("code")).isEqualTo(5030);
        assertThat((String) problem.getProperties().get("timestamp")).isNotBlank();
        assertThat(problem.getProperties().get("traceId")).isEqualTo("trace-conn-503");
        // errors 只在字段校验失败时出现，连接失败沿用既有逻辑不携带
        assertThat(problem.getProperties()).doesNotContainKey("errors");
    }

    /** Spring SQLExceptionSubclassTranslator 的翻译形态：TransientDataAccessResourceException。 */
    @Test
    void transientConnectionFailure_mapsTo503WithRetryAfter() {
        TransientDataAccessResourceException ex = new TransientDataAccessResourceException(
                "Connection is not available",
                new SQLTransientConnectionException(HIKARI_POOL_EXHAUSTED));

        ResponseEntity<?> response = handler.handleConnectionUnavailable(ex, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        ProblemDetail problem = problemOf(response);
        assertThat(problem.getProperties().get("code")).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(problem.getType().toString()).isEqualTo("urn:leo:problem:service-unavailable");
    }

    /** Hikari 原始异常未经翻译直接冒泡时也按依赖不可用处理。 */
    @Test
    void rawHikariTransientConnectionException_mapsTo503() {
        ResponseEntity<?> response = handler.handleConnectionUnavailable(
                new SQLTransientConnectionException(HIKARI_POOL_EXHAUSTED), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(problemOf(response).getProperties().get("code"))
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
    }

    /** DataAccessResourceFailureException 的子类（Spring JDBC 的经典形态）走同一分支。 */
    @Test
    void cannotGetJdbcConnectionSubclass_mapsTo503() {
        ResponseEntity<?> response = handler.handleConnectionUnavailable(
                new CannotGetJdbcConnectionException("Unable to acquire JDBC Connection"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
    }

    /** ServiceUnavailableException（Redis/幂等依赖）与本分支共用 code 5030，互不破坏。 */
    @Test
    void serviceUnavailableException_keepsExisting503Contract() {
        ResponseEntity<?> response = handler.handleServiceUnavailable(
                new ServiceUnavailableException("Redis 不可用"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ProblemDetail problem = problemOf(response);
        assertThat(problem.getProperties().get("code")).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(problem.getType().toString()).isEqualTo("urn:leo:problem:service-unavailable");
    }

    // ------------------------------------------------------------------
    // 既有映射回归：409 / 404 / 422 / 5000 一条都不能变
    // ------------------------------------------------------------------

    /** 数据完整性异常是请求数据的问题，必须留在 409，不能被连接族分支吞掉。 */
    @Test
    void uniqueViolation_stillMapsTo409() {
        SQLException cause = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uk_po_purchase_order_order_no\"", "23505");

        ResponseEntity<?> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("could not execute statement", cause), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(problemOf(response).getProperties().get("code"))
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    /** 乐观锁冲突仍是 409（报价资源仍是 412，见 GlobalExceptionHandlerOptimisticLockTest）。 */
    @Test
    void optimisticLock_stillMapsTo409() {
        ResponseEntity<?> response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("SalesOrder", 9L), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(problemOf(response).getProperties().get("code"))
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    /**
     * 关键回归：悲观锁同属 transient 家族但不是连接族，
     * 必须仍是 409，证明没有按 TransientDataAccessException 父类一刀切。
     */
    @Test
    void cannotAcquireLock_stillMapsTo409() {
        ResponseEntity<?> response = handler.handlePessimisticLockingFailure(
                new CannotAcquireLockException("could not obtain lock"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(problemOf(response).getProperties().get("code"))
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    @Test
    void businessNotFound_stillMapsTo404() {
        ResponseEntity<?> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.NOT_FOUND, "单据不存在"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ProblemDetail problem = problemOf(response);
        assertThat(problem.getProperties().get("code")).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(problem.getType().toString()).isEqualTo("urn:leo:problem:not-found");
    }

    /** 422 分支连同 errors[] 字段一起回归，证明字段机制未被失败响应重构影响。 */
    @Test
    void constraintViolation_stillMapsTo422WithFieldErrors() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Payload>> violations = validator.validate(new Payload(""));

        ResponseEntity<?> response = handler.handleConstraintViolation(
                new ConstraintViolationException(violations), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ProblemDetail problem = problemOf(response);
        assertThat(problem.getProperties().get("code")).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(problem.getProperties()).containsKey("errors");
        assertThat((List<?>) problem.getProperties().get("errors")).hasSize(1);
    }

    @Test
    void noResourceFound_stillMapsTo404() {
        ResponseEntity<?> response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/v2.0/orders/999"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(problemOf(response).getProperties().get("code")).isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    /** 真正的代码缺陷（NPE）仍走兜底 5000，不能被新分支吞掉。 */
    @Test
    void nullPointer_stillMapsTo5000() {
        ResponseEntity<?> response = handler.handleException(new NullPointerException("boom"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail problem = problemOf(response);
        assertThat(problem.getProperties().get("code")).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(problem.getProperties().get("code")).isEqualTo(5000);
        assertThat(problem.getType().toString()).isEqualTo("urn:leo:problem:internal-error");
    }

    // ------------------------------------------------------------------
    // 端到端：真实 Spring 的 @ExceptionHandler 选择结果
    // ------------------------------------------------------------------

    @Test
    void mvc_connectionFailure_returns503WithRetryAfterHeader() throws Exception {
        MDC.put("traceId", "trace-mvc-503");
        controller.failWith(new DataAccessResourceFailureException(
                "Unable to acquire JDBC Connection",
                new SQLTransientConnectionException(HIKARI_POOL_EXHAUSTED)));

        mockMvc.perform(get(PROBE_URI))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.SERVICE_UNAVAILABLE.getCode()))
                .andExpect(jsonPath("$.code").value(5030))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.type").value("urn:leo:problem:service-unavailable"))
                .andExpect(jsonPath("$.detail").value(containsString("连接池")))
                .andExpect(jsonPath("$.instance").value(PROBE_URI))
                .andExpect(jsonPath("$.traceId").value("trace-mvc-503"));
    }

    /** 子类形态必须命中同一分支：验证 Spring 按异常继承链选择 handler，而非仅按声明类型。 */
    @Test
    void mvc_connectionFailureSubclass_returns503() throws Exception {
        controller.failWith(new CannotGetJdbcConnectionException("Unable to acquire JDBC Connection"));

        mockMvc.perform(get(PROBE_URI))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(jsonPath("$.code").value(ErrorCode.SERVICE_UNAVAILABLE.getCode()));
    }

    @Test
    void mvc_transientConnectionFailure_returns503() throws Exception {
        controller.failWith(new TransientDataAccessResourceException(
                "Connection is not available",
                new SQLTransientConnectionException(HIKARI_POOL_EXHAUSTED)));

        mockMvc.perform(get(PROBE_URI))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(jsonPath("$.code").value(ErrorCode.SERVICE_UNAVAILABLE.getCode()));
    }

    /** 锁失败不属于连接族：端到端仍是 409，不能被 503 分支抢走。 */
    @Test
    void mvc_lockFailure_stillReturns409() throws Exception {
        controller.failWith(new CannotAcquireLockException("could not obtain lock"));

        mockMvc.perform(get(PROBE_URI))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_MODIFICATION.getCode()));
    }

    /** 数据完整性异常端到端仍是 409。 */
    @Test
    void mvc_dataIntegrityViolation_stillReturns409() throws Exception {
        SQLException cause = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uk_po_purchase_order_order_no\"", "23505");
        controller.failWith(new DataIntegrityViolationException("could not execute statement", cause));

        mockMvc.perform(get(PROBE_URI))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_MODIFICATION.getCode()));
    }

    /** 代码缺陷端到端仍是 500/5000，且不带 Retry-After（不能引导客户端去重试确定性故障）。 */
    @Test
    void mvc_nullPointer_stillReturns5000() throws Exception {
        controller.failWith(new NullPointerException("boom"));

        mockMvc.perform(get(PROBE_URI))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
                .andExpect(jsonPath("$.code").value(5000));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 仅用于校验 errors[] 字段的样例负载。 */
    record Payload(@NotBlank String name) {
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(PROBE_URI);
        return request;
    }

    private ProblemDetail problemOf(ResponseEntity<?> response) {
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        return problem;
    }

    /** 仅供测试：由用例决定抛出哪个异常，用来验证真实 Spring 的 handler 选择结果。 */
    @RestController
    public static class FailureProbeController {

        private Exception failure;

        public void failWith(Exception failure) {
            this.failure = failure;
        }

        @GetMapping("/api/v2.0/login")
        public ResponseEntity<Void> login() throws Exception {
            throw failure;
        }
    }
}
