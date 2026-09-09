package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderInboundCandidateQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderReferenceQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderQueryServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderReferenceQueryRepository referenceQueryRepository;

    @Mock
    private PurchaseOrderResponseAssembler responseAssembler;

    @Mock
    private PurchaseOrderAvailabilityService availabilityService;

    @Mock
    private PurchaseOrderInboundCandidateQueryRepository inboundCandidateQueryRepository;

    @InjectMocks
    private PurchaseOrderQueryService service;

    @Nested
    class PendingQuery {

        @Test
        void findPending_shouldPassTypedDateBoundsWhenDatesMissing() {
            PageQuery query = new PageQuery(0, 30, null, null);
            PageFilter filter = PageFilter.of(null, null, null, null, null, null);
            when(purchaseOrderRepository.findPending(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

            service.findPending(query, filter);

            ArgumentCaptor<LocalDateTime> startDate = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endDateExclusive = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(purchaseOrderRepository).findPending(
                    any(), any(), any(), any(), any(), startDate.capture(), endDateExclusive.capture(), any(),
                    any(Pageable.class));
            assertThat(startDate.getValue()).isEqualTo(LocalDateTime.of(1, 1, 1, 0, 0));
            assertThat(endDateExclusive.getValue()).isEqualTo(LocalDateTime.of(10000, 1, 1, 0, 0));
        }

        @Test
        void findPending_shouldNormalizeKeywordAndExactFilters() {
            PageQuery query = new PageQuery(0, 30, null, null);
            PageFilter filter = PageFilter.of("  AbC  ", "  供应商  ", 7L, " AUDITED ", null, null);
            when(purchaseOrderRepository.findPending(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

            service.findPending(query, filter);

            verify(purchaseOrderRepository).findPending(
                    eq("abc"), isNull(), eq("供应商"), eq(7L), eq("AUDITED"),
                    any(), any(), any(), any(Pageable.class));
        }

        @Test
        void findPending_shouldUseDefaultBoundsForExplicitDates() {
            PageQuery query = new PageQuery(0, 30, null, null);
            PageFilter filter = PageFilter.of(null, null, null, null,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 9));
            when(purchaseOrderRepository.findPending(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

            service.findPending(query, filter);

            ArgumentCaptor<LocalDateTime> startDate = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endDateExclusive = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(purchaseOrderRepository).findPending(
                    any(), any(), any(), any(), any(), startDate.capture(), endDateExclusive.capture(), any(),
                    any(Pageable.class));
            assertThat(startDate.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
            assertThat(endDateExclusive.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 10, 0, 0));
        }
    }

    @Nested
    class ReferenceFilterQuery {

        @Test
        void findByReferenceFilter_shouldRejectUnknownReferencedBy() {
            PageQuery query = new PageQuery(0, 30, null, null);
            PageFilter filter = PageFilter.of(null, null, null, null, null, null);

            assertThatThrownBy(() -> service.findByReferenceFilter(query, filter, null, null, "unknown"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("下游模块关联筛选值");
        }

        @Test
        void findByReferenceFilter_shouldAcceptKnownReferencedByValues() {
            PageQuery query = new PageQuery(0, 30, null, null);
            PageFilter filter = PageFilter.of(null, null, null, null, null, null);
            when(purchaseOrderRepository.findByReferenceFilter(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

            service.findByReferenceFilter(query, filter, null, null, "none");

            verify(purchaseOrderRepository).findByReferenceFilter(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("none"), any(Pageable.class));
        }
    }

    @Nested
    class InboundImportCandidates {

        @Test
        void inboundImportCandidates_shouldReturnEmptyPageWhenNoCandidates() {
            PageQuery query = new PageQuery(0, 30, null, null);
            PageFilter filter = PageFilter.of(null, null, null, null, null, null);
            Pageable pageable = query.toPageable("id");
            when(inboundCandidateQueryRepository.pageIds(query, filter))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            var result = service.inboundImportCandidates(query, filter);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    class ReferenceStatusEnrichment {

        @Test
        void findReferenceStatusByOrderIds_shouldReturnEmptyMapWhenRepositoryMissing() {
            PurchaseOrderQueryService serviceWithoutReferenceRepository = new PurchaseOrderQueryService(
                    purchaseOrderRepository,
                    null,
                    responseAssembler,
                    availabilityService,
                    inboundCandidateQueryRepository
            );

            assertThat(serviceWithoutReferenceRepository.findReferenceStatusByOrderIds(List.of(1L)))
                    .isEmpty();
        }

        @Test
        void toDetailResponse_shouldReturnAssemblerResponseWhenRepositoryMissing() {
            PurchaseOrderQueryService serviceWithoutReferenceRepository = new PurchaseOrderQueryService(
                    purchaseOrderRepository,
                    null,
                    responseAssembler,
                    availabilityService,
                    inboundCandidateQueryRepository
            );
            PurchaseOrder order = new PurchaseOrder();
            order.setId(1L);
            when(responseAssembler.toDetailResponse(order)).thenReturn(null);

            assertThat(serviceWithoutReferenceRepository.toDetailResponse(order)).isNull();
        }
    }
}
