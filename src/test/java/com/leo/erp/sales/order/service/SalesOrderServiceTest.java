package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderOutboundCandidateQueryRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.repository.SalesOrderReferenceQueryRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOrderService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

    @Mock
    private SalesOrderRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private SalesOrderResponseAssembler responseAssembler;

    @Mock
    private SalesOrderApplyService salesOrderApplyService;

    @Mock
    private SalesOrderAuditedPricingService salesOrderAuditedPricingService;

    @Mock
    private SalesOrderProtectedUpdatePolicy protectedUpdatePolicy;

    @Mock
    private SalesOrderSaveService saveService;

    @Mock
    private com.leo.erp.common.concurrency.SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;

    @Mock
    private SalesOrderDownstreamMutationGuard downstreamMutationGuard;

    @Mock
    private SalesOrderOutboundCandidateQueryRepository outboundCandidateQueryRepository;

    @Mock
    private SalesOrderReferenceQueryRepository referenceQueryRepository;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @Mock
    private DocumentChargeItemService documentChargeItemService;
    @InjectMocks
    private SalesOrderService service;

    @Test
    void page_pendingOnly_shouldUseRepositoryQueryWithoutCrossModuleEntities() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null, null);
        when(repository.findPending(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        Page<SalesOrderResponse> result = service.page(query, filter, null, true);

        assertThat(result.getTotalElements()).isZero();
        verify(repository).findPending(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class));
    }

    @Test
    void page_pendingOnly_shouldPassTypedDateBoundsWhenDatesMissing() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null, null);
        when(repository.findPending(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        service.page(query, filter, null, true);

        ArgumentCaptor<LocalDate> startDate = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endDate = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).findPending(
                any(), any(), any(), any(), any(), any(), any(), any(), startDate.capture(), endDate.capture(),
                any(), any(Pageable.class));
        assertThat(startDate.getValue()).isEqualTo(LocalDate.of(1, 1, 1));
        assertThat(endDate.getValue()).isEqualTo(LocalDate.of(9999, 12, 31));
    }

    @Test
    void page_withReferenceFilter_shouldDelegateToReferenceAwareQuery() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null, null);
        when(repository.findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(false), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), query.toPageable("id"), 0));

        service.page(query, filter, null, false, true);

        verify(repository).findByReferenceFilter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(false), eq(true), any(Pageable.class));
    }

    private SalesOrderRequest request(String orderNo, String status) {
        return new SalesOrderRequest(
                orderNo, null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", status, null, List.of(), List.of(), false);
    }

    private SalesOrder entity(Long ownerUserId, String status) {
        SalesOrder entity = new SalesOrder();
        entity.setId(5L);
        entity.setOrderNo("SO001");
        entity.setOwnerUserId(ownerUserId);
        entity.setStatus(status);
        return entity;
    }

    private void loginAs(Long userId) {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        lenient().when(principal.id()).thenReturn(userId);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // ---------- 单号/导入校验 ----------

    @Test
    void validateCreate_shouldRejectDuplicateOrderNo() {
        when(repository.existsByOrderNoAndDeletedFlagFalse("SO001")).thenReturn(true);

        assertThatThrownBy(() -> service.validateCreate(request("SO001", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单号已存在");
    }

    @Test
    void validateCreate_shouldRejectNonDraftStatus() {
        when(repository.existsByOrderNoAndDeletedFlagFalse("SO001")).thenReturn(false);

        assertThatThrownBy(() -> service.validateCreate(request("SO001", StatusConstants.AUDITED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能保存为草稿");
    }

    @Test
    void validateUpdate_shouldRejectNotOwnedByCurrentUser() {
        loginAs(1L);
        SalesOrder entity = entity(999L, StatusConstants.DRAFT); // 属于他人

        assertThatThrownBy(() -> service.validateUpdate(entity, request("SO001", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本人负责");
    }

    @Test
    void validateUpdate_shouldRejectChangedDuplicateNo() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        when(repository.existsByOrderNoAndDeletedFlagFalse("SO999")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(entity, request("SO999", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateUpdate_shouldPassWhenOwnedAndNoDuplicate() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);

        service.validateUpdate(entity, request("SO001", StatusConstants.DRAFT)); // 不抛
    }

    // ---------- 出库导入候选 ----------

    @Test
    void outboundImportCandidates_shouldMapCandidates() {
        SalesOrder order = entity(1L, StatusConstants.AUDITED);
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(outboundCandidateQueryRepository.pageIds(any(), any()))
                .thenReturn(new PageImpl<>(List.of(5L), org.springframework.data.domain.PageRequest.of(0, 10), 1));
        when(repository.findByIdInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(responseAssembler.toDetailResponse(order)).thenReturn(response);

        Page<SalesOrderResponse> result = service.outboundImportCandidates(
                mock(com.leo.erp.common.api.PageQuery.class), mock(com.leo.erp.common.api.PageFilter.class));

        assertThat(result.getContent()).containsExactly(response);
    }

    // ---------- 删除/状态守卫与事件 ----------

    @Test
    void beforeDelete_shouldGuard() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.AUDITED);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已使用"))
                .when(downstreamMutationGuard).assertMutable(any(), anyString());

        assertThatThrownBy(() -> service.beforeDelete(entity)).isInstanceOf(BusinessException.class);
    }

    @Test
    void afterDelete_shouldPublishEvent() {
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);

        service.afterDelete(entity);

        verify(businessOperationEventPublisher).publish(eq("SALES_ORDER_DELETED"), anyString(), anyString(),
                anyString(), anyString(), eq(5L), anyString(), anyString());
    }

    @Test
    void beforeStatusUpdate_shouldRejectCompleteViaStatus() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                entity, StatusConstants.DRAFT, StatusConstants.SALES_COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("专用完成操作");
    }

    @Test
    void beforeStatusUpdate_shouldGuardDeliveryVerificationReverse() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.SALES_COMPLETED);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已使用"))
                .when(deliveryVerificationGuard).assertMutable(any(), anyString());

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                entity, StatusConstants.SALES_COMPLETED, StatusConstants.DELIVERY_VERIFICATION))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- completeSalesOrder ----------

    @Test
    void completeSalesOrder_shouldRejectWhenNotDeliveryVerification() {
        loginAs(1L);
        SalesOrder order = entity(1L, StatusConstants.DRAFT);
        when(repository.findForUpdateByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.completeSalesOrder(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有交付核定状态可以完成销售");
    }

    @Test
    void completeSalesOrder_shouldCompleteWhenDeliveryVerification() {
        loginAs(1L);
        SalesOrder order = entity(1L, StatusConstants.DELIVERY_VERIFICATION);
        when(repository.findForUpdateByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(order));
        when(saveService.saveStatus(order)).thenReturn(order);
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(responseAssembler.toDetailResponse(order)).thenReturn(response);

        SalesOrderResponse result = service.completeSalesOrder(5L);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.SALES_COMPLETED);
        assertThat(result).isSameAs(response);
        verify(businessOperationEventPublisher).publish(eq("SALES_ORDER_COMPLETED"), anyString(), anyString(),
                anyString(), anyString(), eq(5L), anyString(), anyString());
    }

    // ---------- apply ----------

    @Test
    void apply_shouldApplyWhenCreating() {
        loginAs(1L);
        SalesOrder entity = new SalesOrder();
        entity.setId(5L);
        when(salesOrderAuditedPricingService.isAuditedPricingUpdate(any(), any())).thenReturn(false);

        service.apply(entity, request("SO001", StatusConstants.DRAFT));

        verify(salesOrderApplyService).apply(any(), any(), any());
    }

    @Test
    void apply_shouldUsePricingUpdatePathWhenAuditedPricingUpdate() {
        loginAs(1L);
        SalesOrder entity = new SalesOrder();
        entity.setId(5L);
        when(salesOrderAuditedPricingService.isAuditedPricingUpdate(any(), any())).thenReturn(true);

        service.apply(entity, request("SO001", StatusConstants.AUDITED));

        verify(salesOrderApplyService).validateCustomerSnapshot(any(SalesOrderRequest.class));
        verify(salesOrderAuditedPricingService).applyAuditedPricingUpdate(any(), any());
        verify(salesOrderApplyService, org.mockito.Mockito.never()).apply(any(), any(), any());
    }

    @Test
    void apply_shouldGuardWhenExistingItemsAndNotPricingUpdate() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        com.leo.erp.sales.order.domain.entity.SalesOrderItem item =
                new com.leo.erp.sales.order.domain.entity.SalesOrderItem();
        item.setId(100L);
        entity.setItems(List.of(item));
        when(salesOrderAuditedPricingService.isAuditedPricingUpdate(any(), any())).thenReturn(false);

        service.apply(entity, request("SO001", StatusConstants.DRAFT));

        verify(downstreamMutationGuard).assertNoFreightReference(any(), anyString());
        verify(downstreamMutationGuard).assertSourceLineMutationAllowed(any(), any(), anyString());
        verify(salesOrderApplyService).apply(any(), any(), any());
    }

    // ---------- save 事件 ----------

    @Test
    void saveCreatedEntity_shouldPublishEvent() {
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        when(saveService.save(entity)).thenReturn(entity);

        service.saveCreatedEntity(entity, request("SO001", StatusConstants.DRAFT));

        verify(businessOperationEventPublisher).publish(eq("SALES_ORDER_CREATED"), anyString(), anyString(),
                anyString(), anyString(), eq(5L), anyString(), anyString());
    }

    @Test
    void saveUpdatedEntity_shouldUsePricingSaveWhenAuditedPricingUpdate() {
        SalesOrder entity = entity(1L, StatusConstants.AUDITED);
        when(salesOrderAuditedPricingService.isAuditedPricingUpdate(any(), any())).thenReturn(true);
        when(saveService.saveAuditedPricingUpdate(entity)).thenReturn(entity);

        service.saveUpdatedEntity(entity, request("SO001", StatusConstants.AUDITED));

        verify(saveService).saveAuditedPricingUpdate(entity);
        verify(saveService, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void saveStatusEntity_shouldUseSaveStatus() {
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        when(saveService.saveStatus(entity)).thenReturn(entity);

        assertThat(service.saveStatusEntity(entity)).isSameAs(entity);
    }

    // ---------- 允许写最终状态 ----------

    @Test
    void allowRequestToWriteFinalStatus_shouldAllowDeliveryVerification() {
        SalesOrder entity = entity(1L, StatusConstants.DELIVERY_VERIFICATION);
        SalesOrderRequest request = request("SO001", StatusConstants.DELIVERY_VERIFICATION);

        boolean allowed = service.allowRequestToWriteFinalStatus(
                entity, request, java.util.Optional.of(StatusConstants.DELIVERY_VERIFICATION));

        assertThat(allowed).isTrue();
    }

    // ---------- page ----------

    @Test
    void page_shouldMapEntities() {
        loginAs(1L);
        com.leo.erp.common.api.PageQuery query = mock(com.leo.erp.common.api.PageQuery.class);
        when(query.toPageable("id")).thenReturn(org.springframework.data.domain.PageRequest.of(0, 10));
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(responseAssembler.toSummaryResponse(entity)).thenReturn(response);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));

        Page<SalesOrderResponse> result = service.page(query, mock(com.leo.erp.common.api.PageFilter.class), null);

        assertThat(result.getContent()).containsExactly(response);
    }
}
