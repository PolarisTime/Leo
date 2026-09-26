package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private DocumentChargeItemService documentChargeItemService;

    @Mock
    private SalesOrderQueryService queryService;

    @Mock
    private SalesOrderMutationGuardService mutationGuardService;

    @Mock
    private SalesOrderWorkflowService workflowService;

    @Mock
    private PermissionChecker permissionChecker;

    @Mock
    private SalesOrderPriceRuleService priceRuleService;

    @InjectMocks
    private SalesOrderService service;

    @BeforeEach
    void grantFieldPermissionsByDefault() {
        // 默认放行字段级权限，避免影响与字段权限无关的既有用例。
        lenient().when(permissionChecker.has(anyString())).thenReturn(true);
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
        // 授予全局通配，避免状态变更的受控动作权限门禁干扰单元测试的数据/流程断言。
        lenient().when(auth.getAuthorities())
                .thenReturn((java.util.Collection) List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                PermissionCodes.WILDCARD)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- page 委派 ----------

    @Test
    void page_shouldDelegateToQueryServiceWithDefaults() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null, null);
        Page<SalesOrderResponse> expected = mock(Page.class);
        when(queryService.page(query, filter, null, null, null, null)).thenReturn(expected);

        Page<SalesOrderResponse> result = service.page(query, filter, null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void page_withPendingOnlyAndReferenceFilters_shouldPassThroughAllArguments() {
        PageQuery query = new PageQuery(0, 30, null, null);
        PageFilter filter = PageFilter.of(null, null, null, null, null, null, null);
        Page<SalesOrderResponse> expected = mock(Page.class);
        when(queryService.page(query, filter, "kw", true, true, "freight-bill")).thenReturn(expected);

        Page<SalesOrderResponse> result = service.page(query, filter, "kw", true, true, "freight-bill");

        assertThat(result).isSameAs(expected);
    }

    // ---------- 出库导入候选 ----------

    @Test
    void outboundImportCandidates_shouldDelegateToQueryService() {
        PageQuery query = mock(PageQuery.class);
        PageFilter filter = mock(PageFilter.class);
        Page<SalesOrderResponse> expected = mock(Page.class);
        when(queryService.outboundImportCandidates(query, filter)).thenReturn(expected);

        Page<SalesOrderResponse> result = service.outboundImportCandidates(query, filter);

        assertThat(result).isSameAs(expected);
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
    void validateCreate_shouldAcceptNullStatus() {
        when(repository.existsByOrderNoAndDeletedFlagFalse("SO001")).thenReturn(false);

        service.validateCreate(request("SO001", null));
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
    void validateUpdate_shouldRejectUnauthenticatedUser() {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);

        assertThatThrownBy(() -> service.validateUpdate(entity, request("SO001", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别当前登录账号");
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

    // ---------- 删除/状态守卫委派与事件顺序 ----------

    @Test
    void beforeDelete_shouldDelegateToMutationGuard() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.AUDITED);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "已使用"))
                .when(mutationGuardService).assertDeletable(entity);

        assertThatThrownBy(() -> service.beforeDelete(entity)).isInstanceOf(BusinessException.class);
        verify(mutationGuardService).assertDeletable(entity);
    }

    @Test
    void afterDelete_shouldRemoveChargesBeforePublishingEvent() {
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);

        service.afterDelete(entity);

        InOrder inOrder = inOrder(documentChargeItemService, workflowService);
        inOrder.verify(documentChargeItemService).removeAll("sales-order", 5L);
        inOrder.verify(workflowService).publishDeleted(entity);
    }

    @Test
    void beforeStatusUpdate_shouldDelegateToMutationGuard() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "已使用"))
                .when(mutationGuardService).assertStatusTransitionAllowed(
                        entity, StatusConstants.DRAFT, StatusConstants.SALES_COMPLETED);

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                entity, StatusConstants.DRAFT, StatusConstants.SALES_COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已使用");
    }

    // ---------- completeSalesOrder ----------

    @Test
    void completeSalesOrder_shouldRejectWhenOrderNotFound() {
        when(repository.findForUpdateByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeSalesOrder(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单不存在");
        verifyNoInteractions(workflowService);
    }

    @Test
    void completeSalesOrder_shouldDelegateOwnedOrderToWorkflow() {
        loginAs(1L);
        SalesOrder order = entity(1L, StatusConstants.DELIVERY_VERIFICATION);
        when(repository.findForUpdateByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(order));
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(workflowService.completeSalesOrder(order)).thenReturn(response);

        SalesOrderResponse result = service.completeSalesOrder(5L);

        assertThat(result).isSameAs(response);
    }

    // ---------- apply / save 委派 ----------

    @Test
    void apply_shouldDelegateToWorkflowWithIdSupplier() {
        loginAs(1L);
        SalesOrder entity = new SalesOrder();
        entity.setId(5L);
        SalesOrderRequest req = request("SO001", StatusConstants.DRAFT);

        service.apply(entity, req);

        verify(workflowService).apply(eq(entity), eq(req), any());
    }

    @Test
    void saveCreatedEntity_shouldDelegateToWorkflow() {
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        when(workflowService.saveCreated(entity, request("SO001", StatusConstants.DRAFT))).thenReturn(entity);

        assertThat(service.saveCreatedEntity(entity, request("SO001", StatusConstants.DRAFT))).isSameAs(entity);
    }

    @Test
    void saveUpdatedEntity_shouldDelegateToWorkflow() {
        SalesOrder entity = entity(1L, StatusConstants.AUDITED);
        when(workflowService.saveUpdated(entity, request("SO001", StatusConstants.AUDITED))).thenReturn(entity);

        assertThat(service.saveUpdatedEntity(entity, request("SO001", StatusConstants.AUDITED))).isSameAs(entity);
    }

    @Test
    void saveStatusEntity_shouldDelegateToWorkflow() {
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        when(workflowService.saveStatus(entity)).thenReturn(entity);

        assertThat(service.saveStatusEntity(entity)).isSameAs(entity);
    }

    // ---------- 允许写最终状态 ----------

    @Test
    void allowRequestToWriteFinalStatus_shouldAllowDeliveryVerification() {
        SalesOrder entity = entity(1L, StatusConstants.DELIVERY_VERIFICATION);
        SalesOrderRequest req = request("SO001", StatusConstants.DELIVERY_VERIFICATION);

        boolean allowed = service.allowRequestToWriteFinalStatus(
                entity, req, Optional.of(StatusConstants.DELIVERY_VERIFICATION));

        assertThat(allowed).isTrue();
    }

    @Test
    void allowRequestToWriteFinalStatus_shouldRejectOtherStatuses() {
        SalesOrder entity = entity(1L, StatusConstants.AUDITED);
        SalesOrderRequest req = request("SO001", StatusConstants.AUDITED);

        boolean allowed = service.allowRequestToWriteFinalStatus(entity, req, Optional.empty());

        assertThat(allowed).isFalse();
    }

    // ---------- 显式状态断言序列（原基类内联） ----------

    @Test
    void update_shouldRejectIllegalStatusTransitionAfterApply() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(documentChargeItemService.list("sales-order", 5L)).thenReturn(List.of());
        when(mutationGuardService.allowsProtectedUpdate(eq(entity), any())).thenReturn(true);
        doAnswer(invocation -> {
            entity.setStatus(StatusConstants.SALES_COMPLETED);
            return null;
        }).when(workflowService).apply(eq(entity), any(), any());

        assertThatThrownBy(() -> service.update(5L, request("SO001", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「已审核」变更为「完成销售」");

        verify(workflowService, never()).saveUpdated(any(), any());
    }

    @Test
    void update_shouldRejectUnitPriceChangeWithoutPermission() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        SalesOrderItem existingItem = new SalesOrderItem();
        existingItem.setId(11L);
        existingItem.setUnitPrice(new BigDecimal("100.00"));
        entity.setItems(new ArrayList<>(List.of(existingItem)));
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(documentChargeItemService.list("sales-order", 5L)).thenReturn(List.of());
        when(mutationGuardService.allowsProtectedUpdate(eq(entity), any())).thenReturn(true);
        when(permissionChecker.has(PermissionCodes.SALES_ORDERS_UPDATE_UNIT_PRICE)).thenReturn(false);

        SalesOrderItemRequest item = new SalesOrderItemRequest(
                11L, "M001", "品牌", "品类", "材质", "规格", "9米", "吨",
                null, 100L, "仓库", "批号", 5, "件", BigDecimal.ONE, 0, BigDecimal.TEN,
                new BigDecimal("200.00"), new BigDecimal("2000.00"));
        SalesOrderRequest request = new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", "草稿", null,
                List.of(item), List.of(), false);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("sales-orders:update:unit-price");

        verify(workflowService, never()).saveUpdated(any(), any());
    }

    @Test
    void update_shouldRejectWritingFinalStatusThroughSave() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.SALES_COMPLETED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(documentChargeItemService.list("sales-order", 5L)).thenReturn(List.of());
        when(mutationGuardService.allowsProtectedUpdate(eq(entity), any())).thenReturn(true);
        doAnswer(invocation -> {
            entity.setStatus(StatusConstants.DELIVERY_VERIFICATION);
            return null;
        }).when(workflowService).apply(eq(entity), any(), any());

        assertThatThrownBy(() -> service.update(5L, request("SO001", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("完成态状态必须通过专用状态接口变更");

        verify(workflowService, never()).saveUpdated(any(), any());
    }

    @Test
    void updateAndComplete_shouldBypassFinalStatusGuardForDeliveryVerification() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.DELIVERY_VERIFICATION);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(repository.findForUpdateByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(mutationGuardService.allowsProtectedUpdate(eq(entity), any())).thenReturn(true);
        when(workflowService.saveUpdated(eq(entity), any())).thenReturn(entity);
        SalesOrderResponse completed = mock(SalesOrderResponse.class);
        when(workflowService.completeSalesOrder(entity)).thenReturn(completed);

        SalesOrderResponse result =
                service.updateAndComplete(5L, request("SO001", StatusConstants.DELIVERY_VERIFICATION));

        assertThat(result).isSameAs(completed);
        verify(workflowService).saveUpdated(eq(entity), any());
        verify(workflowService).completeSalesOrder(entity);
    }

    @Test
    void updateAndComplete_shouldRejectStatusChangeOutsideSpecializedOperation() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(
                () -> service.updateAndComplete(5L, request("SO001", StatusConstants.DELIVERY_VERIFICATION)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单状态只能通过审核、反审核或完成销售操作变更");
    }

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(5L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");

        verify(workflowService, never()).saveStatus(any());
        verify(workflowService, never()).publishStatusChanged(any(), anyString(), anyString());
    }

    @Test
    void updateStatus_shouldRejectTransitionOutsideTable() {
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(5L, StatusConstants.SALES_COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「完成销售」");

        verify(mutationGuardService, never()).assertStatusTransitionAllowed(any(), anyString(), anyString());
        verify(workflowService, never()).saveStatus(any());
    }

    @Test
    void updateStatus_shouldGuardThenSaveThenPublishInOrder() {
        loginAs(1L);
        SalesOrder entity = entity(1L, StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(workflowService.saveStatus(entity)).thenReturn(entity);
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(queryService.toDetailResponse(entity)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.AUDITED);

        InOrder inOrder = inOrder(mutationGuardService, workflowService);
        inOrder.verify(mutationGuardService)
                .assertStatusTransitionAllowed(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);
        inOrder.verify(workflowService).saveStatus(entity);
        inOrder.verify(workflowService).publishStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);
    }
}
