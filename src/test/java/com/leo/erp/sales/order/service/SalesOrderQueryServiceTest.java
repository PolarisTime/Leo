package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderOutboundCandidateQueryRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOrderQueryService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderQueryServiceTest {

    @Mock
    private SalesOrderRepository repository;

    @Mock
    private SalesOrderOutboundCandidateQueryRepository outboundCandidateQueryRepository;

    @Mock
    private SalesOrderResponseAssembler responseAssembler;

    @Mock
    private SalesOrderDerivedQuantityService derivedQuantityService;

    @Mock
    private ProjectQuery projectQuery;

    @InjectMocks
    private SalesOrderQueryService service;

    private SalesOrder entity() {
        SalesOrder entity = new SalesOrder();
        entity.setId(5L);
        entity.setOrderNo("SO001");
        return entity;
    }

    private PageQuery query() {
        return new PageQuery(0, 30, null, null);
    }

    private PageFilter filter() {
        return PageFilter.of(null, null, null, null, null, null, null);
    }

    // ---------- page ----------

    @Test
    void page_pendingOnly_shouldUseReferenceAwareQueryWithPendingCombination() {
        PageQuery query = query();
        // pendingOnly=true 走 JPQL 的待处理分支，等价于已删除的 repository.findPending 查询。
        when(repository.findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        Page<SalesOrderResponse> result = service.page(query, filter(), null, true);

        assertThat(result.getTotalElements()).isZero();
        verify(repository).findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(true), any(Pageable.class));
    }

    @Test
    void page_pendingOnly_shouldPassTypedDateBoundsWhenDatesMissing() {
        PageQuery query = query();
        when(repository.findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        service.page(query, filter(), null, true);

        ArgumentCaptor<LocalDate> startDate = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endDate = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), startDate.capture(), endDate.capture(),
                any(), eq(true), any(Pageable.class));
        assertThat(startDate.getValue()).isEqualTo(LocalDate.of(1, 1, 1));
        assertThat(endDate.getValue()).isEqualTo(LocalDate.of(9999, 12, 31));
    }

    @Test
    void page_shouldMapEntitiesAndAttachDerivedQuantities() {
        PageQuery query = query();
        SalesOrder order = entity();
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        SalesOrderResponse summary = mock(SalesOrderResponse.class);
        when(responseAssembler.toSummaryResponse(order)).thenReturn(summary);
        when(derivedQuantityService.orderQuantities(List.of(5L))).thenReturn(Map.of());
        when(summary.withDerivedQuantities(0, 0, 0)).thenReturn(summary);

        Page<SalesOrderResponse> result = service.page(query, filter(), null, null);

        assertThat(result.getContent()).containsExactly(summary);
    }

    // ---------- outboundImportCandidates ----------

    @Test
    void outboundImportCandidates_shouldMapCandidates() {
        SalesOrder order = entity();
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(outboundCandidateQueryRepository.pageIds(any(), any()))
                .thenReturn(new PageImpl<>(List.of(5L), PageRequest.of(0, 10), 1));
        when(repository.findByIdInAndDeletedFlagFalse(anyList())).thenReturn(List.of(order));
        when(responseAssembler.toDetailResponses(List.of(order))).thenReturn(List.of(response));

        Page<SalesOrderResponse> result = service.outboundImportCandidates(
                mock(PageQuery.class), mock(PageFilter.class));

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(responseAssembler).toDetailResponses(List.of(order));
    }

    @Test
    void outboundImportCandidates_shouldReturnEmptyPageWhenNoCandidateIds() {
        when(outboundCandidateQueryRepository.pageIds(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        Page<SalesOrderResponse> result = service.outboundImportCandidates(
                mock(PageQuery.class), mock(PageFilter.class));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verifyNoInteractions(repository);
        verifyNoInteractions(responseAssembler);
    }

    @Test
    void outboundImportCandidates_shouldSkipMissingOrders() {
        when(outboundCandidateQueryRepository.pageIds(any(), any()))
                .thenReturn(new PageImpl<>(List.of(5L, 6L), PageRequest.of(0, 10), 2));
        SalesOrder order = entity();
        when(repository.findByIdInAndDeletedFlagFalse(anyList())).thenReturn(List.of(order));
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(responseAssembler.toDetailResponses(List.of(order))).thenReturn(List.of(response));

        Page<SalesOrderResponse> result = service.outboundImportCandidates(
                mock(PageQuery.class), mock(PageFilter.class));

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ---------- 项目简称填充 ----------

    @Test
    void page_shouldFillProjectNameAbbrWithSingleBatchQueryPerPage() {
        PageQuery query = query();
        SalesOrder first = entity();
        SalesOrder second = new SalesOrder();
        second.setId(6L);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(first, second)));
        SalesOrderResponse firstSummary = mock(SalesOrderResponse.class);
        SalesOrderResponse secondSummary = mock(SalesOrderResponse.class);
        when(firstSummary.projectId()).thenReturn(20L);
        when(secondSummary.projectId()).thenReturn(21L);
        when(responseAssembler.toSummaryResponse(first)).thenReturn(firstSummary);
        when(responseAssembler.toSummaryResponse(second)).thenReturn(secondSummary);
        when(derivedQuantityService.orderQuantities(List.of(5L, 6L))).thenReturn(Map.of());
        when(firstSummary.withDerivedQuantities(0, 0, 0)).thenReturn(firstSummary);
        when(secondSummary.withDerivedQuantities(0, 0, 0)).thenReturn(secondSummary);
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of(
                20L, new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001"),
                21L, new ProjectQuery.ProjectSnapshot(21L, "项目B", "项B", 10L, "CUST001")));

        Page<SalesOrderResponse> result = service.page(query, filter(), null, null);

        assertThat(result.getContent()).extracting(SalesOrderResponse::projectNameAbbr)
                .containsExactly("项A", "项B");
        verify(projectQuery, times(1)).findActiveByIds(anyCollection());
    }

    @Test
    void page_shouldLeaveProjectNameAbbrNullWhenProjectMissing() {
        PageQuery query = query();
        SalesOrder order = entity();
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        SalesOrderResponse summary = mock(SalesOrderResponse.class);
        when(summary.projectId()).thenReturn(99L);
        when(responseAssembler.toSummaryResponse(order)).thenReturn(summary);
        when(derivedQuantityService.orderQuantities(List.of(5L))).thenReturn(Map.of());
        when(summary.withDerivedQuantities(0, 0, 0)).thenReturn(summary);
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of());

        Page<SalesOrderResponse> result = service.page(query, filter(), null, null);

        assertThat(result.getContent()).containsExactly(summary);
        assertThat(result.getContent().get(0).projectNameAbbr()).isNull();
        verify(projectQuery, times(1)).findActiveByIds(anyCollection());
    }

    @Test
    void toDetailResponse_shouldFillProjectNameAbbr() {
        SalesOrder order = entity();
        SalesOrderResponse detail = mock(SalesOrderResponse.class);
        when(detail.projectId()).thenReturn(20L);
        when(responseAssembler.toDetailResponse(order)).thenReturn(detail);
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of(
                20L, new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001")));

        assertThat(service.toDetailResponse(order).projectNameAbbr()).isEqualTo("项A");
    }

    // ---------- 响应装配 ----------

    @Test
    void toSummaryResponse_shouldDelegateToAssembler() {
        SalesOrder order = entity();
        SalesOrderResponse summary = mock(SalesOrderResponse.class);
        when(responseAssembler.toSummaryResponse(order)).thenReturn(summary);

        assertThat(service.toSummaryResponse(order)).isSameAs(summary);
    }

    @Test
    void toDetailResponse_shouldDelegateToAssembler() {
        SalesOrder order = entity();
        SalesOrderResponse detail = mock(SalesOrderResponse.class);
        when(responseAssembler.toDetailResponse(order)).thenReturn(detail);

        assertThat(service.toDetailResponse(order)).isSameAs(detail);
    }

    private static <T> T eq(T value) {

        return org.mockito.ArgumentMatchers.eq(value);
    }
}
