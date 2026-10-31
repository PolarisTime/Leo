package com.leo.erp.common.exception;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发/前置条件冲突的 HTTP 映射契约。
 *
 * <p>分流依据只有异常类型与 {@link ErrorCode}，不看请求 URI：</p>
 * <ul>
 *   <li>JPA 乐观锁冲突 {@code ObjectOptimisticLockingFailureException}/{@code OptimisticLockException}
 *       → 409 {@code CONCURRENT_MODIFICATION}（任何路径都一致，含 quote-sheets / quote-project-configs）；</li>
 *   <li>业务层显式抛出的 {@code BusinessException}：{@code CONCURRENT_MODIFICATION} → 409、
 *       {@code PRECONDITION_FAILED} → 412、{@code PRECONDITION_REQUIRED} → 428；</li>
 *   <li>悲观锁获取失败（等待超时/死锁）→ 409。</li>
 * </ul>
 */
class GlobalExceptionHandlerOptimisticLockTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai"));

    /**
     * 回归：报价单路径上的乐观锁冲突不再按 URI 子串特殊化为 412，统一 409。
     * 412 只由业务层显式抛出的 {@code PRECONDITION_FAILED} 表达。
     */
    @Test
    void quoteSheetOptimisticLock_noLongerSpecialCasedByUri_mapsTo409() {
        MockHttpServletRequest request = request("/v2.0/quote-sheets/9");

        ResponseEntity<?> response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("QuoteSheet", 9L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    @Test
    void quoteProjectConfigOptimisticLock_mapsTo409() {
        MockHttpServletRequest request = request("/v2.0/quote-project-configs/88");

        ResponseEntity<?> response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("QuoteProjectConfig", 88L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    @Test
    void jakartaOptimisticLock_mapsTo409() {
        MockHttpServletRequest request = request("/v2.0/quote-sheets/9/items/301");

        ResponseEntity<?> response = handler.handleOptimisticLockingFailure(
                new OptimisticLockException("QuoteSheet"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    @Test
    void nonQuoteOptimisticLock_mapsTo409() {
        MockHttpServletRequest request = request("/v2.0/sales-orders/9");

        ResponseEntity<?> response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("SalesOrder", 9L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    @Test
    void nullRequestOptimisticLock_defaultsTo409() {
        ResponseEntity<?> response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("SalesOrder", 9L), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** 资源版本前置条件不满足：业务层抛 PRECONDITION_FAILED → 412。 */
    @Test
    void businessPreconditionFailed_mapsTo412() {
        MockHttpServletRequest request = request("/v2.0/quote-sheets/9");

        ResponseEntity<?> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.PRECONDITION_FAILED, "报价单版本已变更，请刷新后重试"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.PRECONDITION_FAILED.getCode());
    }

    /** 缺少版本前置条件：业务层抛 PRECONDITION_REQUIRED → 428。 */
    @Test
    void businessPreconditionRequired_mapsTo428() {
        MockHttpServletRequest request = request("/v2.0/sales-contracts/9");

        ResponseEntity<?> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.PRECONDITION_REQUIRED, "缺少 X-Resource-Version"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.PRECONDITION_REQUIRED.getCode());
    }

    /** 业务层并发冲突仍按 409 表达（与乐观锁异常同一状态码，同一语义入口）。 */
    @Test
    void businessConcurrentModification_mapsTo409() {
        MockHttpServletRequest request = request("/api/v2/anything");

        ResponseEntity<?> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "数据已被其他请求修改"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    /** 悲观锁获取失败(等待超时/取消 NOWAIT)统一映射为 409, 避免长时间阻塞后 500。 */
    @Test
    void cannotAcquireLock_mapsTo409() {
        MockHttpServletRequest request = request("/v2.0/quote-sheets/9/edit-lock");

        ResponseEntity<?> response = handler.handlePessimisticLockingFailure(
                new CannotAcquireLockException("could not obtain lock"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    /** 死锁等悲观锁失败同样映射为 409 并发冲突。 */
    @Test
    void genericPessimisticLockingFailure_mapsTo409() {
        MockHttpServletRequest request = request("/v2.0/quote-sheets/9/edit-lock");

        ResponseEntity<?> response = handler.handlePessimisticLockingFailure(
                new PessimisticLockingFailureException("deadlock detected"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    private int codeOf(ResponseEntity<?> response) {
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        return (int) problem.getProperties().get("code");
    }
}
