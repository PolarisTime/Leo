package com.leo.erp.common.exception;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据完整性错误的 HTTP 映射契约：
 * 唯一键冲突与外键引用冲突返回 409 且文案明确，
 * 字段长度/数值类完整性错误保留 422 语义。
 */
class GlobalExceptionHandlerDataIntegrityTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai"));

    @Test
    void foreignKeyRestrictViolation_mapsTo409WithReferenceMessage() {
        SQLException cause = new SQLException(
                "ERROR: update or delete on table \"po_purchase_order_item\" violates RESTRICT setting of "
                        + "foreign key constraint \"fk_po_purchase_inbound_item_source_identity\" "
                        + "on table \"po_purchase_inbound_item\"",
                "23001");

        ResponseEntity<?> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("could not execute statement", cause), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail problem = problemOf(response);
        assertThat(problem.getProperties().get("code")).isEqualTo(ErrorCode.BUSINESS_ERROR.getCode());
        assertThat(problem.getDetail()).contains("被其他单据引用");
    }

    @Test
    void foreignKeyViolationWithoutSqlState_mapsTo409ByMessage() {
        SQLException cause = new SQLException(
                "ERROR: insert or update on table \"po_purchase_inbound_item\" violates foreign key constraint "
                        + "\"fk_po_purchase_inbound_item_source_identity\"");

        ResponseEntity<?> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("could not execute statement", cause), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(problemOf(response).getProperties().get("code"))
                .isEqualTo(ErrorCode.BUSINESS_ERROR.getCode());
    }

    @Test
    void uniqueViolation_mapsTo409WithConflictMessage() {
        SQLException cause = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uk_po_purchase_order_order_no\"", "23505");

        ResponseEntity<?> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("could not execute statement", cause), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail problem = problemOf(response);
        assertThat(problem.getProperties().get("code"))
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.getCode());
        assertThat(problem.getDetail()).contains("数据已存在");
    }

    @Test
    void lengthViolation_keepsValidationSemantics() {
        SQLException cause = new SQLException("value too long for type character varying(64)", "22001");

        ResponseEntity<?> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("could not execute statement", cause), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ProblemDetail problem = problemOf(response);
        assertThat(problem.getProperties().get("code")).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(problem.getDetail()).contains("字段长度或数值超出允许范围");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v2.0/purchase-orders/1");
        return request;
    }

    private ProblemDetail problemOf(ResponseEntity<?> response) {
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        return problem;
    }
}
