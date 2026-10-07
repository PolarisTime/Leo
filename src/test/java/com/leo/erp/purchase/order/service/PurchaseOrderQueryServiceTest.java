package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderInboundCandidateQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderReferenceQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
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

import java.math.BigDecimal;
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

        @Test
        void applyReferenceFlags_shouldCarryUnreceivedQuantityOntoResponse() {
            PurchaseOrderResponse base = new PurchaseOrderResponse(
                    1L, "PO-1", null, null, "供应商", null, null, null, null,
                    null, null, "已审核", false, null, List.of()
            );

            PurchaseOrderResponse enriched = service.applyReferenceFlags(
                    base,
                    new PurchaseOrderReferenceQueryRepository.ReferenceStatus(
                            1L, true, false, 7,
                            new BigDecimal("1200.50"), new BigDecimal("200.50"))
            );

            assertThat(enriched.referencedBySalesOrder()).isTrue();
            assertThat(enriched.referencedByPurchaseInbound()).isFalse();
            assertThat(enriched.totalRemainingQuantity()).isEqualTo(7);
            assertThat(enriched.totalActualAmount()).isEqualByComparingTo("1200.50");
            assertThat(enriched.totalAmountDifference()).isEqualByComparingTo("200.50");
        }

        /** actual_weight_ton 全为 NULL 时 SQL 兜底为 0，差额等于负的暂定金额。 */
        @Test
        void applyReferenceFlags_shouldCarryZeroActualAmountAndNegativeDifference() {
            PurchaseOrderResponse base = new PurchaseOrderResponse(
                    1L, "PO-1", null, null, "供应商", null, null, null, null,
                    null, new BigDecimal("800.00"), "已审核", false, null, List.of()
            );

            PurchaseOrderResponse enriched = service.applyReferenceFlags(
                    base,
                    new PurchaseOrderReferenceQueryRepository.ReferenceStatus(
                            1L, false, false, 0,
                            BigDecimal.ZERO, new BigDecimal("-800.00"))
            );

            assertThat(enriched.totalActualAmount()).isEqualByComparingTo("0");
            assertThat(enriched.totalAmountDifference()).isEqualByComparingTo("-800.00");
        }

        @Test
        void applyReferenceFlags_shouldCarryPositiveDifferenceWhenActualExceedsTentative() {
            PurchaseOrderResponse base = new PurchaseOrderResponse(
                    1L, "PO-1", null, null, "供应商", null, null, null, null,
                    null, new BigDecimal("500.00"), "已审核", false, null, List.of()
            );

            PurchaseOrderResponse enriched = service.applyReferenceFlags(
                    base,
                    new PurchaseOrderReferenceQueryRepository.ReferenceStatus(
                            1L, false, false, 0,
                            new BigDecimal("700.00"), new BigDecimal("200.00"))
            );

            assertThat(enriched.totalAmountDifference()).isEqualByComparingTo("200.00");
        }

        @Test
        void applyReferenceFlags_shouldCarryZeroDifference() {
            PurchaseOrderResponse base = new PurchaseOrderResponse(
                    1L, "PO-1", null, null, "供应商", null, null, null, null,
                    null, new BigDecimal("500.00"), "已审核", false, null, List.of()
            );

            PurchaseOrderResponse enriched = service.applyReferenceFlags(
                    base,
                    new PurchaseOrderReferenceQueryRepository.ReferenceStatus(
                            1L, false, false, 0,
                            new BigDecimal("500.00"), BigDecimal.ZERO)
            );

            assertThat(enriched.totalAmountDifference()).isEqualByComparingTo("0");
        }

        @Test
        void applyDifference_shouldPassthroughIncludingNullValues() {
            PurchaseOrderResponse base = new PurchaseOrderResponse(
                    1L, "PO-1", null, null, "供应商", null, null, null, null,
                    null, null, "已审核", false, null, List.of()
            );

            PurchaseOrderResponse withValues = base.applyDifference(
                    new BigDecimal("123.45"), new BigDecimal("-23.45"));
            assertThat(withValues.totalActualAmount()).isEqualByComparingTo("123.45");
            assertThat(withValues.totalAmountDifference()).isEqualByComparingTo("-23.45");

            PurchaseOrderResponse withNulls = base.applyDifference(null, null);
            assertThat(withNulls.totalActualAmount()).isNull();
            assertThat(withNulls.totalAmountDifference()).isNull();
        }

        @Test
        void applyReferenceFlags_shouldLeaveResponseUntouchedWhenStatusMissing() {
            PurchaseOrderResponse base = new PurchaseOrderResponse(
                    1L, "PO-1", null, null, "供应商", null, null, null, null,
                    null, null, "已审核", false, null, List.of()
            );

            assertThat(service.applyReferenceFlags(base, null)).isSameAs(base);
        }
    }
}
