package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    // ---------- 显式状态断言序列（原基类内联） ----------

    private PurchaseOrder order(String status) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(5L);
        order.setOrderNo("PO001");
        order.setStatus(status);
        return order;
    }

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        when(purchaseOrderRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(order(StatusConstants.DRAFT)));

        assertThatThrownBy(() -> service.updateStatus(5L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");

        verify(purchaseOrderRepository, never()).save(any(PurchaseOrder.class));
        verifyNoInteractions(purchaseOrderAuditPublisher);
    }

    @Test
    void updateStatus_shouldRejectTransitionOutsideTable() {
        when(purchaseOrderRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(order(StatusConstants.DRAFT)));

        assertThatThrownBy(() -> service.updateStatus(5L, StatusConstants.PURCHASE_COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「完成采购」");

        verify(mutationGuardService, never()).assertMutable(any(), anyString());
        verify(purchaseOrderRepository, never()).save(any(PurchaseOrder.class));
        verifyNoInteractions(purchaseOrderAuditPublisher);
    }

    @Test
    void updateStatus_shouldShortCircuitWhenStatusUnchanged() {
        PurchaseOrder entity = order(StatusConstants.AUDITED);
        when(purchaseOrderRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        PurchaseOrderResponse response = mock(PurchaseOrderResponse.class);
        when(queryService.toDetailResponse(entity)).thenReturn(response);

        assertThat(service.updateStatus(5L, StatusConstants.AUDITED)).isSameAs(response);

        verify(mutationGuardService, never()).assertMutable(any(), anyString());
        verify(purchaseOrderRepository, never()).save(any(PurchaseOrder.class));
        verifyNoInteractions(purchaseOrderAuditPublisher);
    }

    @Test
    void updateStatus_shouldGuardThenSaveThenPublishReverseAuditInOrder() {
        PurchaseOrder entity = order(StatusConstants.AUDITED);
        when(purchaseOrderRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(purchaseOrderRepository.save(entity)).thenReturn(entity);
        PurchaseOrderResponse response = mock(PurchaseOrderResponse.class);
        when(response.status()).thenReturn(StatusConstants.DRAFT);
        when(queryService.toDetailResponse(entity)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.DRAFT);

        InOrder inOrder = inOrder(mutationGuardService, purchaseOrderRepository, purchaseOrderAuditPublisher);
        inOrder.verify(mutationGuardService).assertMutable(entity, "反审核");
        inOrder.verify(purchaseOrderRepository).save(entity);
        inOrder.verify(purchaseOrderAuditPublisher).publish(
                eq(entity), eq("PURCHASE_ORDER_REVERSE_AUDITED"), eq("反审核"), anyString());
    }

    @Test
    void update_shouldRejectStatusChangeThroughSave() {
        PurchaseOrder entity = order(StatusConstants.DRAFT);
        when(purchaseOrderRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "PO001", 1L, "SUP001", "供应商A", LocalDateTime.of(2026, 8, 1, 0, 0),
                "采购员A", 2L, StatusConstants.AUDITED, null, List.of(), List.of(), false);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("普通保存不能修改采购订单状态");

        verify(purchaseOrderRepository, never()).save(any(PurchaseOrder.class));
        verifyNoInteractions(supplierResolver, purchaseOrderAuditPublisher);
    }
}
