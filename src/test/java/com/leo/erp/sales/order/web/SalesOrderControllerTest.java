package com.leo.erp.sales.order.web;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactDraft;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.web.ExportArtifactResponse;
import com.leo.erp.sales.order.service.SalesOrderXlsxExportArtifactService;
import org.springframework.core.io.Resource;
import com.leo.erp.common.export.ExportArtifactProperties;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.InMemoryExportArtifactStore;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.service.SalesOrderSourceCandidateService;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderOutboundImportCandidatesCriteria;
import com.leo.erp.sales.order.web.dto.SalesOrderPageCriteria;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOrderController 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderControllerTest {

    @Mock
    private SalesOrderService service;

    @Mock
    private SalesOrderXlsxExportArtifactService xlsxExportArtifactService;

    @org.mockito.Spy
    private ExportArtifactStore xlsxExportArtifactStore =
            new InMemoryExportArtifactStore(new SnowflakeIdGenerator(), new ExportArtifactProperties());

    @Mock
    private SalesOrderSourceCandidateService sourceCandidateService;

    @Mock
    private com.leo.erp.sales.order.service.SalesOrderDocumentFlowService documentFlowService;

    /** 订单版本前置条件开关（默认开启，见 {@code SalesOrderProperties}）。 */
    @Mock
    private com.leo.erp.sales.order.SalesOrderProperties properties;

    @InjectMocks
    private SalesOrderController controller;

    @BeforeEach
    void setUpServletContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... authorities) {
        var granted = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken(
                SecurityPrincipal.authenticated(1L, "tester", 0L), "n/a", granted);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private SalesOrderRequest request() {
        return new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", "DRAFT", null, List.of(), List.of(), false);
    }

    @Test
    void page_shouldDelegate() {
        when(service.page(any(PageQuery.class), any(PageFilter.class), any(), isNull()))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.page(mock(PageQuery.class), criteria("kw", 10L, "客户A", 20L, "项目A", 30L,
                "M001", "DRAFT", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null));

        assertThat(result).isNotNull();
    }

    @Test
    void page_shouldForwardPendingOnlyFilter() {
        when(service.page(any(PageQuery.class), any(PageFilter.class), anyString(), any()))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.page(mock(PageQuery.class), criteria("kw", 10L, "客户A", 20L, "项目A", 30L,
                "M001", "DRAFT", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), true));

        assertThat(result).isNotNull();
        verify(service).page(any(PageQuery.class), any(PageFilter.class), anyString(), eq(true));
    }

    private SalesOrderPageCriteria criteria(String keyword, Long customerId, String customerName,
                                            Long projectId, String projectName, Long settlementCompanyId,
                                            String productKeyword, String status, LocalDate startDate, LocalDate endDate,
                                            Boolean pendingOnly) {
        SalesOrderPageCriteria criteria = new SalesOrderPageCriteria();
        criteria.setKeyword(keyword);
        criteria.setCustomerId(customerId);
        criteria.setCustomerName(customerName);
        criteria.setProjectId(projectId);
        criteria.setProjectName(projectName);
        criteria.setSettlementCompanyId(settlementCompanyId);
        criteria.setProductKeyword(productKeyword);
        criteria.setStatus(status);
        criteria.setStartDate(startDate);
        criteria.setEndDate(endDate);
        criteria.setPendingOnly(pendingOnly);
        return criteria;
    }

    @Test
    void outboundImportCandidates_shouldDelegate() {
        when(service.outboundImportCandidates(any(PageQuery.class), any(PageFilter.class)))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        SalesOrderOutboundImportCandidatesCriteria criteria = new SalesOrderOutboundImportCandidatesCriteria();
        criteria.setKeyword("kw");
        criteria.setCustomerId(10L);
        criteria.setCustomerName("客户A");
        criteria.setProjectId(20L);
        criteria.setProjectName("项目A");
        criteria.setSettlementCompanyId(30L);
        criteria.setStatus("DRAFT");
        criteria.setStartDate(LocalDate.of(2026, 8, 1));
        criteria.setEndDate(LocalDate.of(2026, 8, 31));
        criteria.setCurrentRecordId(5L);

        var result = controller.outboundImportCandidates(mock(PageQuery.class), criteria);

        assertThat(result).isNotNull();
    }

    @Test
    void sourceCandidates_shouldDelegate() {
        @SuppressWarnings("unchecked")
        PageResponse<SalesOrderSourceCandidateResponse> expected =
                (PageResponse<SalesOrderSourceCandidateResponse>) mock(PageResponse.class);
        when(sourceCandidateService.page(anyString(), anyLong(), anyLong(), any(), any(), anyLong(), any(PageQuery.class)))
                .thenReturn(expected);

        var result = controller.sourceCandidates(mock(PageQuery.class), "kw", 1L, 2L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5L);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void detail_shouldDelegate() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.detail(5L)).thenReturn(response);

        assertThat(controller.detail(5L)).isSameAs(response);
    }

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExportArtifact storeArtifact(Long orderId, byte[] content) {
        return xlsxExportArtifactStore.put(new ExportArtifactDraft(
                ExportArtifactKinds.SALES_ORDER_XLSX_EXPORT,
                SalesOrderXlsxExportArtifactService.PARENT_TYPE,
                orderId,
                1L,
                "SO001.xlsx",
                XLSX_MEDIA_TYPE,
                content
        ));
    }

    @Test
    void createXlsxExportRequest_shouldReturn201WithLocationAndContentPath() {
        ExportArtifact artifact = storeArtifact(5L, new byte[]{1, 2, 3});
        when(xlsxExportArtifactService.create(eq(5L), any(), any())).thenReturn(artifact);

        ResponseEntity<ExportArtifactResponse> result = controller.createXlsxExportRequest(5L, null, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getHeaders().getLocation().getPath())
                .endsWith("/v2.0/sales-orders/5/xlsx-export-requests/" + artifact.id());
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().id()).isEqualTo(artifact.id());
        assertThat(result.getBody().contentPath())
                .isEqualTo("/v2.0/sales-orders/5/xlsx-export-requests/" + artifact.id() + "/content");
    }

    @Test
    void xlsxExportRequestContent_shouldReturnBytesForMatchingOrder() throws Exception {
        ExportArtifact artifact = storeArtifact(5L, new byte[]{1, 2, 3});

        ResponseEntity<Resource> result = controller.xlsxExportRequestContent(null, 5L, artifact.id());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getContentType().toString()).isEqualTo(XLSX_MEDIA_TYPE);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getInputStream().readAllBytes()).containsExactly(1, 2, 3);
    }

    /** 产物从属于销售订单：拿别的订单 id 读取按 404 处理，不泄露存在性。 */
    @Test
    void xlsxExportRequestContent_withOtherOrder_shouldReturn404() {
        ExportArtifact artifact = storeArtifact(99L, new byte[]{1});

        assertThatThrownBy(() -> controller.xlsxExportRequestContent(null, 5L, artifact.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void xlsxExportRequestContent_withUnknownExportId_shouldReturn404() {
        assertThatThrownBy(() -> controller.xlsxExportRequestContent(null, 5L, 123456789L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void create_shouldReturnCreated() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.create(any())).thenReturn(response);

        ResponseEntity<SalesOrderResponse> result = controller.create(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void update_shouldDelegate() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(properties.isRequireResourceVersion()).thenReturn(true);
        when(service.update(anyLong(), any(), eq(3L))).thenReturn(response);

        assertThat(controller.update(5L, "3", request())).isSameAs(response);
    }

    @Test
    void createDeliveryVerification_shouldReturnOkPointingToParentOrder() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.updateAndComplete(eq(5L), any(SalesOrderRequest.class))).thenReturn(response);

        ResponseEntity<SalesOrderResponse> result = controller.createDeliveryVerification(5L, request());

        // 交付核定是状态迁移 + 保存, 不是创建资源: 200 + 最新订单, 不发 Location
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
        assertThat(result.getHeaders().getLocation()).isNull();
        verify(service).updateAndComplete(eq(5L), any(SalesOrderRequest.class));
    }

    @Test
    void updateStatus_shouldPassStatus() {
        authenticate(PermissionCodes.SALES_ORDERS_AUDIT);
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.updateStatus(anyLong(), anyString())).thenReturn(response);

        assertThat(controller.updateStatus(5L, new StatusUpdateRequest("AUDITED"))).isSameAs(response);
        verify(service).updateStatus(5L, "AUDITED");
    }

    @Test
    void createCompletion_shouldReturnOkPointingToParentOrder() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.completeSalesOrder(eq(5L))).thenReturn(response);

        ResponseEntity<SalesOrderResponse> result = controller.createCompletion(5L);

        // 完成销售是状态迁移, 不是创建资源: 200 + 最新订单, 不发 Location
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
        assertThat(result.getHeaders().getLocation()).isNull();
        verify(service).completeSalesOrder(5L);
    }

    @Test
    void delete_shouldReturnNoContent() {
        org.mockito.Mockito.doNothing().when(service).delete(anyLong());

        ResponseEntity<Void> result = controller.delete(5L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
