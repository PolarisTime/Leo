package com.leo.erp.sales.contract.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.ResourceVersionPrecondition;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.contract.SalesContractProperties;
import com.leo.erp.sales.contract.service.SalesContractService;
import com.leo.erp.sales.contract.web.dto.SalesContractRequest;
import com.leo.erp.sales.contract.web.dto.SalesContractResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2SalesContractControllerTest {

    @Mock
    private SalesContractService service;

    private SalesContractProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SalesContractProperties();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private V2SalesContractController controller(boolean requireVersion) {
        properties.setRequireResourceVersion(requireVersion);
        return new V2SalesContractController(service, properties);
    }

    @Test
    void create_returnsCreatedWithLocation() {
        when(service.create(any())).thenReturn(response(3L));

        ResponseEntity<SalesContractResponse> entity = controller(false).create(request());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getHeaders().getLocation()).isNotNull();
        assertThat(entity.getHeaders().getLocation().getPath()).endsWith("/sales-contracts/100");
    }

    @Test
    void delete_returnsNoContent() {
        doNothing().when(service).delete(eq(100L));

        assertThat(controller(false).delete(100L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void update_withMatchingVersion_returnsVersionHeader() {
        when(service.update(eq(100L), any(), eq(3L))).thenReturn(response(3L));

        ResponseEntity<SalesContractResponse> entity = controller(true).update(100L, "3", null, request());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getHeaders().getFirst(ResourceVersionPrecondition.HEADER)).isEqualTo("3");
    }

    @Test
    void update_missingVersion_whenRequired_isRejectedWith428() {
        assertThatThrownBy(() -> controller(true).update(100L, null, null, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRECONDITION_REQUIRED);
    }

    @Test
    void update_missingVersion_whenOptional_forwardsNull() {
        when(service.update(eq(100L), any(), isNull())).thenReturn(response(3L));

        ResponseEntity<SalesContractResponse> entity = controller(false).update(100L, null, null, request());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).update(eq(100L), any(), isNull());
    }

    @Test
    void update_acceptsIfMatchAlias() {
        when(service.update(eq(100L), any(), eq(3L))).thenReturn(response(3L));

        ResponseEntity<SalesContractResponse> entity = controller(true).update(100L, null, "W/\"3\"", request());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void update_conflictingVersionHeaders_isRejected() {
        assertThatThrownBy(() -> controller(true).update(100L, "3", "\"4\"", request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void updateStatus_returnsVersionHeader() {
        when(service.updateStatus(100L, "已审核")).thenReturn(auditedResponse(4L));

        ResponseEntity<SalesContractResponse> entity =
                controller(false).updateStatus(100L, new StatusUpdateRequest("已审核"));

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody().status()).isEqualTo("已审核");
        assertThat(entity.getHeaders().getFirst(ResourceVersionPrecondition.HEADER)).isEqualTo("4");
    }

    private SalesContractRequest request() {
        return new SalesContractRequest(
                null,
                "年度钢材采购合同",
                1L,
                2L,
                LocalDate.of(2026, 9, 17),
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("1000000.00"),
                new BigDecimal("3000.50000000"),
                null,
                "备注"
        );
    }

    private SalesContractResponse response(Long version) {
        return new SalesContractResponse(
                100L, "HT-1", "年度钢材采购合同",
                1L, "客户甲", 2L, "项目乙",
                LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 18), LocalDate.of(2026, 12, 31),
                new BigDecimal("1000000.00"), new BigDecimal("3000.50000000"),
                "草稿", "备注", null, null, version
        );
    }

    private SalesContractResponse auditedResponse(Long version) {
        return new SalesContractResponse(
                100L, "HT-1", "年度钢材采购合同",
                1L, "客户甲", 2L, "项目乙",
                LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 18), LocalDate.of(2026, 12, 31),
                new BigDecimal("1000000.00"), new BigDecimal("3000.50000000"),
                "已审核", "备注", null, null, version
        );
    }
}
