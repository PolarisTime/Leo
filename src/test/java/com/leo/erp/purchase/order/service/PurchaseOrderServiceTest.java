package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.purchase.api.PurchaseOrderPrepaymentReferenceGuard;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.repository.PurchaseOrderInboundCandidateQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderReferenceQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private PurchaseOrderAvailabilityService availabilityService;

    @Mock
    private PurchaseOrderResponseAssembler responseAssembler;

    @Mock
    private PurchaseOrderSupplierResolver supplierResolver;

    @Mock
    private PurchaseOrderApplyService purchaseOrderApplyService;

    @Mock
    private CompanySettingService companySettingService;

    @Mock
    private PurchaseOrderPrepaymentReferenceGuard purchasePrepaymentReferenceGuard;

    @Mock
    private PurchaseOrderDownstreamMutationGuard downstreamMutationGuard;

    @Mock
    private PurchaseOrderAuditPublisher purchaseOrderAuditPublisher;

    @Mock
    private PurchaseOrderInboundCandidateQueryRepository inboundCandidateQueryRepository;

    @Mock
    private DocumentChargeItemService documentChargeItemService;

    @Mock
    private PurchaseOrderReferenceQueryRepository referenceQueryRepository;

    @InjectMocks
    private PurchaseOrderService service;

    @Test
    void page_pendingOnly_shouldPassTypedDateBoundsWhenDatesMissing() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null);
        when(purchaseOrderRepository.findPending(
                any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        service.page(query, filter, true);

        ArgumentCaptor<LocalDateTime> startDate = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endDateExclusive = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(purchaseOrderRepository).findPending(
                any(), any(), any(), any(), any(), startDate.capture(), endDateExclusive.capture(), any(),
                any(Pageable.class));
        assertThat(startDate.getValue()).isEqualTo(LocalDateTime.of(1, 1, 1, 0, 0));
        assertThat(endDateExclusive.getValue()).isEqualTo(LocalDateTime.of(10000, 1, 1, 0, 0));
    }

    @Test
    void page_withReferenceFilter_shouldDelegateToReferenceAwareQuery() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null);
        when(purchaseOrderRepository.findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), eq(false), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        service.page(query, filter, false, true);

        verify(purchaseOrderRepository).findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), eq(false), eq(true), any(Pageable.class));
    }
}
