package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private PurchaseOrderQueryService queryService;

    @Mock
    private PurchaseOrderMutationGuardService mutationGuardService;

    @Mock
    private PurchaseOrderResponseAssembler responseAssembler;

    @Mock
    private PurchaseOrderSupplierResolver supplierResolver;

    @Mock
    private PurchaseOrderApplyService purchaseOrderApplyService;

    @Mock
    private PurchaseOrderAuditPublisher purchaseOrderAuditPublisher;

    @InjectMocks
    private PurchaseOrderService service;

    @Test
    void page_pendingOnly_shouldDelegateToQueryService() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null);
        when(queryService.findPending(eq(query), eq(filter)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));
        when(queryService.findReferenceStatusByOrderIds(any())).thenReturn(Map.of());

        service.page(query, filter, true);

        verify(queryService).findPending(eq(query), eq(filter));
    }

    @Test
    void page_withReferenceFilter_shouldDelegateToQueryService() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null);
        when(queryService.findByReferenceFilter(eq(query), eq(filter), eq(false), eq(true), isNull()))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));
        when(queryService.findReferenceStatusByOrderIds(any())).thenReturn(Map.of());

        service.page(query, filter, false, true);

        verify(queryService).findByReferenceFilter(eq(query), eq(filter), eq(false), eq(true), isNull());
    }

    @Test
    void page_withReferencedByFilter_shouldDelegateToQueryService() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null);
        when(queryService.findByReferenceFilter(eq(query), eq(filter), isNull(), isNull(), eq("purchase-inbound")))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));
        when(queryService.findReferenceStatusByOrderIds(any())).thenReturn(Map.of());

        service.page(query, filter, null, null, "purchase-inbound");

        verify(queryService).findByReferenceFilter(eq(query), eq(filter), isNull(), isNull(), eq("purchase-inbound"));
    }

    @Test
    void page_default_shouldUseSummarySpecificationAndPageEntities() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null);
        when(queryService.summarySpecification(eq(filter))).thenReturn(null);
        when(purchaseOrderRepository.findAll(ArgumentMatchers.<Specification<PurchaseOrder>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));
        when(queryService.findReferenceStatusByOrderIds(any())).thenReturn(Map.of());

        service.page(query, filter);

        verify(queryService).summarySpecification(eq(filter));
        verify(purchaseOrderRepository).findAll(ArgumentMatchers.<Specification<PurchaseOrder>>any(), any(Pageable.class));
        verify(queryService).findReferenceStatusByOrderIds(any());
    }
}
