package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderOutboundCandidateQueryRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.repository.SalesOrderReferenceQueryRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private SalesOrderReferenceQueryRepository referenceQueryRepository;

    @Mock
    private SalesOrderResponseAssembler responseAssembler;

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
        // pendingOnly=true 且 referenced/referencedBy 为 null，等价于已删除的 repository.findPending 查询。
        when(repository.findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(true), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        Page<SalesOrderResponse> result = service.page(query, filter(), null, true, null, null);

        assertThat(result.getTotalElements()).isZero();
        verify(repository).findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(true), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void page_pendingOnly_shouldPassTypedDateBoundsWhenDatesMissing() {
        PageQuery query = query();
        when(repository.findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(true), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        service.page(query, filter(), null, true, null, null);

        ArgumentCaptor<LocalDate> startDate = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endDate = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), startDate.capture(), endDate.capture(),
                any(), eq(true), isNull(), isNull(), any(Pageable.class));
        assertThat(startDate.getValue()).isEqualTo(LocalDate.of(1, 1, 1));
        assertThat(endDate.getValue()).isEqualTo(LocalDate.of(9999, 12, 31));
    }

    @Test
    void page_withReferenceFilter_shouldDelegateToReferenceAwareQuery() {
        PageQuery query = query();
        when(repository.findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(false), eq(true), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        service.page(query, filter(), null, false, true, null);

        verify(repository).findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(false), eq(true), isNull(), any(Pageable.class));
    }

    @Test
    void page_withReferencedByFilter_shouldDelegateToReferenceAwareQuery() {
        PageQuery query = query();
        when(repository.findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), isNull(), eq("freight-bill"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        service.page(query, filter(), null, null, null, "freight-bill");

        verify(repository).findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), isNull(), eq("freight-bill"), any(Pageable.class));
    }

    @Test
    void page_withUnknownReferencedBy_shouldRejectRequest() {
        assertThatThrownBy(() -> service.page(query(), filter(), null, null, null, "unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游模块关联筛选值");
        verifyNoInteractions(repository);
    }

    @Test
    void page_shouldMapEntitiesAndAttachReferenceFlags() {
        PageQuery query = query();
        SalesOrder order = entity();
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        SalesOrderResponse summary = mock(SalesOrderResponse.class);
        SalesOrderResponse flagged = mock(SalesOrderResponse.class);
        when(responseAssembler.toSummaryResponse(order)).thenReturn(summary);
        SalesOrderReferenceQueryRepository.ReferenceStatus status =
                mock(SalesOrderReferenceQueryRepository.ReferenceStatus.class);
        when(status.referencedByFreightBill()).thenReturn(true);
        when(status.referencedBySalesOutbound()).thenReturn(false);
        when(referenceQueryRepository.findByOrderIds(List.of(5L))).thenReturn(Map.of(5L, status));
        when(summary.withReferenceFlags(true, false)).thenReturn(flagged);

        Page<SalesOrderResponse> result = service.page(query, filter(), null, null, null, null);

        assertThat(result.getContent()).containsExactly(flagged);
    }

    @Test
    void page_shouldKeepSummaryWhenNoReferenceStatus() {
        PageQuery query = query();
        SalesOrder order = entity();
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        SalesOrderResponse summary = mock(SalesOrderResponse.class);
        when(responseAssembler.toSummaryResponse(order)).thenReturn(summary);
        when(referenceQueryRepository.findByOrderIds(List.of(5L))).thenReturn(Map.of());

        Page<SalesOrderResponse> result = service.page(query, filter(), null, null, null, null);

        assertThat(result.getContent()).containsExactly(summary);
        verify(summary, never()).withReferenceFlags(org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    // ---------- outboundImportCandidates ----------

    @Test
    void outboundImportCandidates_shouldMapCandidates() {
        SalesOrder order = entity();
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(outboundCandidateQueryRepository.pageIds(any(), any()))
                .thenReturn(new PageImpl<>(List.of(5L), PageRequest.of(0, 10), 1));
        when(repository.findByIdInAndDeletedFlagFalse(anyList())).thenReturn(List.of(order));
        when(responseAssembler.toDetailResponse(order)).thenReturn(response);

        Page<SalesOrderResponse> result = service.outboundImportCandidates(
                mock(PageQuery.class), mock(PageFilter.class));

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(1);
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
    }

    @Test
    void outboundImportCandidates_shouldSkipMissingOrders() {
        when(outboundCandidateQueryRepository.pageIds(any(), any()))
                .thenReturn(new PageImpl<>(List.of(5L, 6L), PageRequest.of(0, 10), 2));
        when(repository.findByIdInAndDeletedFlagFalse(anyList())).thenReturn(List.of(entity()));
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(responseAssembler.toDetailResponse(any(SalesOrder.class))).thenReturn(response);

        Page<SalesOrderResponse> result = service.outboundImportCandidates(
                mock(PageQuery.class), mock(PageFilter.class));

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(1);
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
    void toDetailResponse_shouldAttachReferenceFlagsWhenPresent() {
        SalesOrder order = entity();
        SalesOrderResponse detail = mock(SalesOrderResponse.class);
        SalesOrderResponse flagged = mock(SalesOrderResponse.class);
        when(responseAssembler.toDetailResponse(order)).thenReturn(detail);
        SalesOrderReferenceQueryRepository.ReferenceStatus status =
                mock(SalesOrderReferenceQueryRepository.ReferenceStatus.class);
        when(status.referencedByFreightBill()).thenReturn(false);
        when(status.referencedBySalesOutbound()).thenReturn(true);
        when(referenceQueryRepository.findByOrderIds(List.of(5L))).thenReturn(Map.of(5L, status));
        when(detail.withReferenceFlags(false, true)).thenReturn(flagged);

        assertThat(service.toDetailResponse(order)).isSameAs(flagged);
    }

    @Test
    void toDetailResponse_shouldKeepDetailWhenNoReferenceStatus() {
        SalesOrder order = entity();
        SalesOrderResponse detail = mock(SalesOrderResponse.class);
        when(responseAssembler.toDetailResponse(order)).thenReturn(detail);
        when(referenceQueryRepository.findByOrderIds(List.of(5L))).thenReturn(Map.of());

        assertThat(service.toDetailResponse(order)).isSameAs(detail);
    }

    @Test
    void toDetailResponse_shouldKeepDetailForUnknownOrder() {
        SalesOrder order = entity();
        order.setId(404L);
        SalesOrderResponse detail = mock(SalesOrderResponse.class);
        when(responseAssembler.toDetailResponse(order)).thenReturn(detail);
        when(referenceQueryRepository.findByOrderIds(List.of(404L))).thenReturn(Map.of());

        assertThat(service.toDetailResponse(order)).isSameAs(detail);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
