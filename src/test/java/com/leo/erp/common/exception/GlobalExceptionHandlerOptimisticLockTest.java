package com.leo.erp.common.exception;

import com.leo.erp.common.api.ApiProblemFactory;
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
 * 乐观锁冲突的 HTTP 映射契约: 报价资源使用资源版本前置条件语义(412), 其它模块沿用并发冲突语义(409)。
 */
class GlobalExceptionHandlerOptimisticLockTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai"));

    @Test
    void quoteSheetOptimisticLock_mapsTo412() {
        MockHttpServletRequest request = request("/v2.0/quote-sheets/9");

        ResponseEntity<?> response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("QuoteSheet", 9L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.PRECONDITION_FAILED.getCode());
    }

    @Test
    void quoteProjectConfigOptimisticLock_mapsTo412() {
        MockHttpServletRequest request = request("/v2.0/quote-project-configs/88");

        ResponseEntity<?> response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("QuoteProjectConfig", 88L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.PRECONDITION_FAILED.getCode());
    }

    @Test
    void jakartaOptimisticLockOnQuoteResource_mapsTo412() {
        MockHttpServletRequest request = request("/v2.0/quote-sheets/9/items/301");

        ResponseEntity<?> response = handler.handleOptimisticLockingFailure(
                new OptimisticLockException("QuoteSheet"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(codeOf(response)).isEqualTo(ErrorCode.PRECONDITION_FAILED.getCode());
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
