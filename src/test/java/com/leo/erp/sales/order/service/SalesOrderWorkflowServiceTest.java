package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOrderWorkflowService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderWorkflowServiceTest {

    @Mock
    private SalesOrderSaveService saveService;

    @Mock
    private SalesOrderApplyService salesOrderApplyService;

    @Mock
    private SalesOrderAuditedPricingService salesOrderAuditedPricingService;

    @Mock
    private SalesOrderMutationGuardService mutationGuardService;

    @Mock
    private SalesOrderQueryService queryService;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @InjectMocks
    private SalesOrderWorkflowService service;

    private SalesOrderRequest request(String status) {
        return new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", status, null, List.of(), List.of(), false);
    }

    private SalesOrder entity(String status) {
        SalesOrder entity = new SalesOrder();
        entity.setId(5L);
        entity.setOrderNo("SO001");
        entity.setStatus(status);
        return entity;
    }

    // ---------- apply ----------

    @Test
    void apply_shouldApplyWhenCreating() {
        SalesOrder entity = entity(null);
        SalesOrderRequest req = request(StatusConstants.DRAFT);

        service.apply(entity, req, () -> 6L);

        verify(mutationGuardService).lockPurchaseSources(entity, req);
        verify(mutationGuardService).assertItemMutationAllowed(entity, req, false);
        ArgumentCaptor<LongSupplier> nextId = ArgumentCaptor.forClass(LongSupplier.class);
        verify(salesOrderApplyService).apply(eq(entity), eq(req), nextId.capture());
        assertThat(nextId.getValue().getAsLong()).isEqualTo(6L);
    }

    @Test
    void apply_shouldUsePricingUpdatePathWhenAuditedPricingUpdate() {
        SalesOrder entity = entity(StatusConstants.AUDITED);
        when(salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, request(StatusConstants.AUDITED)))
                .thenReturn(true);

        service.apply(entity, request(StatusConstants.AUDITED), () -> 6L);

        verify(salesOrderApplyService).validateCustomerSnapshot(any(SalesOrderRequest.class));
        verify(salesOrderAuditedPricingService).applyAuditedPricingUpdate(entity, request(StatusConstants.AUDITED));
        verify(salesOrderApplyService, never()).apply(any(), any(), any());
    }

    @Test
    void apply_shouldGuardBeforeApplyingForExistingItems() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        entity.setItems(List.of());
        SalesOrderRequest req = request(StatusConstants.DRAFT);
        when(salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, req)).thenReturn(false);

        service.apply(entity, req, () -> 6L);

        InOrder inOrder = inOrder(mutationGuardService, salesOrderAuditedPricingService,
                salesOrderApplyService);
        inOrder.verify(mutationGuardService).lockPurchaseSources(entity, req);
        inOrder.verify(salesOrderAuditedPricingService).isAuditedPricingUpdate(entity, req);
        inOrder.verify(mutationGuardService).assertItemMutationAllowed(entity, req, false);
        inOrder.verify(salesOrderApplyService).apply(eq(entity), eq(req), any(LongSupplier.class));
    }

    // ---------- save ----------

    @Test
    void save_shouldDelegateToSaveService() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        when(saveService.save(entity)).thenReturn(entity);

        assertThat(service.save(entity)).isSameAs(entity);
    }

    @Test
    void saveStatus_shouldDelegateToSaveService() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        when(saveService.saveStatus(entity)).thenReturn(entity);

        assertThat(service.saveStatus(entity)).isSameAs(entity);
    }

    @Test
    void saveCreated_shouldSaveThenPublishCreatedEvent() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        when(saveService.save(entity)).thenReturn(entity);

        SalesOrder result = service.saveCreated(entity, request(StatusConstants.DRAFT));

        assertThat(result).isSameAs(entity);
        InOrder inOrder = inOrder(saveService, businessOperationEventPublisher);
        inOrder.verify(saveService).save(entity);
        inOrder.verify(businessOperationEventPublisher).publish(
                eq("SALES_ORDER_CREATED"), eq("sales-order"), eq("销售订单"), eq("新增"),
                eq("SalesOrder"), eq(5L), eq("SO001"), eq("新增销售订单 SO001"));
    }

    @Test
    void saveUpdated_shouldUsePricingSaveWhenAuditedPricingUpdate() {
        SalesOrder entity = entity(StatusConstants.AUDITED);
        SalesOrderRequest req = request(StatusConstants.AUDITED);
        when(salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, req)).thenReturn(true);
        when(saveService.saveAuditedPricingUpdate(entity)).thenReturn(entity);

        SalesOrder result = service.saveUpdated(entity, req);

        assertThat(result).isSameAs(entity);
        verify(saveService).saveAuditedPricingUpdate(entity);
        verify(saveService, never()).save(any());
        verify(businessOperationEventPublisher).publish(
                eq("SALES_ORDER_UPDATED"), anyString(), anyString(), anyString(),
                anyString(), eq(5L), anyString(), anyString());
    }

    @Test
    void saveUpdated_shouldUseOrdinarySaveOtherwise() {
        SalesOrder entity = entity(StatusConstants.AUDITED);
        SalesOrderRequest req = request(StatusConstants.AUDITED);
        when(salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, req)).thenReturn(false);
        when(saveService.save(entity)).thenReturn(entity);

        SalesOrder result = service.saveUpdated(entity, req);

        assertThat(result).isSameAs(entity);
        verify(saveService).save(entity);
        verify(saveService, never()).saveAuditedPricingUpdate(any());
    }

    // ---------- completeSalesOrder ----------

    @Test
    void completeSalesOrder_shouldRejectWhenStatusMissing() {
        SalesOrder order = entity(null);

        assertThatThrownBy(() -> service.completeSalesOrder(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有交付核定状态可以完成销售");
        verifyNoInteractions(saveService, businessOperationEventPublisher);
    }

    @Test
    void completeSalesOrder_shouldRejectWhenNotDeliveryVerification() {
        SalesOrder order = entity(StatusConstants.DRAFT);

        assertThatThrownBy(() -> service.completeSalesOrder(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有交付核定状态可以完成销售");
        verifyNoInteractions(saveService, businessOperationEventPublisher);
    }

    @Test
    void completeSalesOrder_shouldBeIdempotentWhenAlreadyCompleted() {
        SalesOrder order = entity(StatusConstants.SALES_COMPLETED);
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(queryService.toDetailResponse(order)).thenReturn(response);

        SalesOrderResponse result = service.completeSalesOrder(order);

        assertThat(result).isSameAs(response);
        verifyNoInteractions(saveService, salesOrderApplyService, businessOperationEventPublisher);
    }

    @Test
    void completeSalesOrder_shouldCompleteWhenDeliveryVerification() {
        SalesOrder order = entity(StatusConstants.DELIVERY_VERIFICATION);
        when(saveService.saveStatus(order)).thenReturn(order);
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(queryService.toDetailResponse(order)).thenReturn(response);

        SalesOrderResponse result = service.completeSalesOrder(order);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.SALES_COMPLETED);
        assertThat(result).isSameAs(response);
        InOrder inOrder = inOrder(salesOrderApplyService, saveService, businessOperationEventPublisher);
        inOrder.verify(salesOrderApplyService).validateCustomerSnapshot(order);
        inOrder.verify(saveService).saveStatus(order);
        inOrder.verify(businessOperationEventPublisher).publish(
                eq("SALES_ORDER_COMPLETED"), eq("sales-order"), eq("销售订单"), eq("完成销售"),
                eq("SalesOrder"), eq(5L), eq("SO001"),
                eq("销售订单状态 " + StatusConstants.DELIVERY_VERIFICATION + " -> " + StatusConstants.SALES_COMPLETED));
    }

    @Test
    void completeSalesOrder_shouldRejectWhenSnapshotInvalid() {
        SalesOrder order = entity(StatusConstants.DELIVERY_VERIFICATION);
        org.mockito.Mockito.doThrow(new BusinessException(
                        com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "客户不一致"))
                .when(salesOrderApplyService).validateCustomerSnapshot(order);

        assertThatThrownBy(() -> service.completeSalesOrder(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户不一致");
        assertThat(order.getStatus()).isEqualTo(StatusConstants.DELIVERY_VERIFICATION);
        verifyNoInteractions(saveService, businessOperationEventPublisher);
    }

    // ---------- 事件 ----------

    @Test
    void publishStatusChanged_shouldUseAuditActionForDraftToAudited() {
        service.publishStatusChanged(entity(StatusConstants.DRAFT), StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(businessOperationEventPublisher).publish(
                eq("SALES_ORDER_STATUS_CHANGED"), eq("sales-order"), eq("销售订单"), eq("审核"),
                eq("SalesOrder"), eq(5L), eq("SO001"),
                eq("销售订单状态 " + StatusConstants.DRAFT + " -> " + StatusConstants.AUDITED));
    }

    @Test
    void publishStatusChanged_shouldUseReverseActionWhenReturningToDraft() {
        service.publishStatusChanged(entity(StatusConstants.AUDITED), StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(businessOperationEventPublisher).publish(
                eq("SALES_ORDER_STATUS_CHANGED"), anyString(), anyString(), eq("反审核"),
                anyString(), eq(5L), anyString(), anyString());
    }

    @Test
    void publishStatusChanged_shouldUseGenericActionOtherwise() {
        service.publishStatusChanged(entity(StatusConstants.AUDITED),
                StatusConstants.AUDITED, StatusConstants.DELIVERY_VERIFICATION);

        verify(businessOperationEventPublisher).publish(
                eq("SALES_ORDER_STATUS_CHANGED"), anyString(), anyString(), eq("状态变更"),
                anyString(), eq(5L), anyString(), anyString());
    }

    @Test
    void publishDeleted_shouldPublishDeletedEvent() {
        SalesOrder entity = entity(StatusConstants.DRAFT);

        service.publishDeleted(entity);

        verify(businessOperationEventPublisher).publish(
                eq("SALES_ORDER_DELETED"), eq("sales-order"), eq("销售订单"), eq("删除"),
                eq("SalesOrder"), eq(5L), eq("SO001"), eq("删除销售订单 SO001"));
    }
}
