package com.leo.erp.purchase.order.service;

import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.api.PurchaseOrderSalesAllocation;
import com.leo.erp.purchase.api.PurchaseSupplierLedgerLock;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 强制结单口径测试。
 *
 * <p>覆盖三类边界: 合法结单(状态/留痕/台账锁/审计), 各类拒绝条件(状态不符、原因非法、
 * 无未入库件数、存在未审核入库草稿、直接销售占用超已入库), 以及撤销结单。</p>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderForceCloseServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderAvailabilityService availabilityService;

    @Mock
    private PurchaseOrderDirectSalesCapacityGuard directSalesCapacityGuard;

    @Mock
    private PurchaseInboundItemQueryService purchaseInboundItemQueryService;

    @Mock
    private PurchaseSupplierLedgerLock supplierLedgerLock;

    @Mock
    private PurchaseOrderAuditPublisher auditPublisher;

    @Mock
    private PurchaseOrderQueryService queryService;

    @Mock
    private AccountQuery accountQuery;

    private PurchaseOrderForceCloseService service;

    private static final SecurityPrincipal OPERATOR =
            SecurityPrincipal.authenticated(9L, "admin_prod", 0L);

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderForceCloseService(
                purchaseOrderRepository,
                availabilityService,
                directSalesCapacityGuard,
                purchaseInboundItemQueryService,
                supplierLedgerLock,
                auditPublisher,
                queryService,
                accountQuery
        );
        lenient().when(purchaseOrderRepository.saveAndFlush(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(queryService.toDetailResponse(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> {
                    PurchaseOrder order = invocation.getArgument(0);
                    return new PurchaseOrderResponse(
                            1L, order.getOrderNo(), null, null, "供应商", null, null, null, null,
                            null, null, order.getStatus(), false, null, List.of(), List.of(),
                            false, false, 0, null, null, null, null
                    );
                });
    }

    private PurchaseOrder order(String status, int orderedQuantity, int receivedQuantity) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-1");
        order.setStatus(status);
        order.setSettlementCompanyId(10L);
        order.setSupplierId(20L);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(100L);
        item.setQuantity(orderedQuantity);
        item.setPurchaseOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));
        lenient().when(availabilityService.loadInboundAllocatedQuantityMap(order))
                .thenReturn(Map.of(100L, receivedQuantity));
        // remainingQuantity = 订货量 − 有效入库量
        lenient().when(availabilityService.remainingQuantity(any(PurchaseOrderItem.class), any()))
                .thenAnswer(invocation -> {
                    PurchaseOrderItem source = invocation.getArgument(0);
                    Map<Long, Integer> allocated = invocation.getArgument(1);
                    return Math.max(0, source.getQuantity() - allocated.getOrDefault(source.getId(), 0));
                });
        return order;
    }

    private void stubOrder(PurchaseOrder order) {
        when(purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
    }

    // ---------- 结单成功 ----------

    @Test
    void forceClose_shouldWriteOffRemainingAndCompleteOrder() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 10, 9);
        stubOrder(order);
        when(purchaseInboundItemQueryService.findAllActiveBySourcePurchaseOrderItemIds(List.of(100L)))
                .thenReturn(List.of());
        when(accountQuery.findById(9L)).thenReturn(Optional.of(
                new AccountQuery.AccountSnapshot(9L, "admin_prod", "系统管理员", null)));

        service.forceClose(1L, "  剩余 1 件报废  ", OPERATOR);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.PURCHASE_COMPLETED);
        assertThat(order.isForceClosed()).isTrue();
        assertThat(order.getForceCloseReason()).isEqualTo("剩余 1 件报废");
        assertThat(order.getForceCloseRemainingQuantity()).isEqualTo(1);
        assertThat(order.getForceClosedBy()).isEqualTo(9L);
        assertThat(order.getForceClosedName()).isEqualTo("系统管理员");
        assertThat(order.getForceClosedAt()).isNotNull();
        verify(supplierLedgerLock).lock(10L, 20L);
        verify(directSalesCapacityGuard).assertCovered(List.of(100L));
        verify(auditPublisher).publish(order, "PURCHASE_ORDER_FORCE_CLOSED", "强制结单",
                "采购订单状态 已审核 -> 完成采购(强制结单), 作废未入库 1 件; 原因: 剩余 1 件报废");
    }

    @Test
    void forceClose_shouldFallBackToLoginNameWhenAccountMissing() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 5, 0);
        stubOrder(order);
        when(purchaseInboundItemQueryService.findAllActiveBySourcePurchaseOrderItemIds(List.of(100L)))
                .thenReturn(List.of());
        when(accountQuery.findById(9L)).thenReturn(Optional.empty());

        service.forceClose(1L, "整单作废", OPERATOR);

        assertThat(order.getForceClosedName()).isEqualTo("admin_prod");
        assertThat(order.getForceCloseRemainingQuantity()).isEqualTo(5);
    }

    @Test
    void forceClose_shouldRecordSystemOperatorWithoutAuthenticatedPrincipal() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 3, 1);
        stubOrder(order);
        when(purchaseInboundItemQueryService.findAllActiveBySourcePurchaseOrderItemIds(List.of(100L)))
                .thenReturn(List.of());

        service.forceClose(1L, "调度缺货", null);

        assertThat(order.getForceClosedBy()).isZero();
        assertThat(order.getForceClosedName()).isEqualTo("system");
        verifyNoInteractions(accountQuery);
    }

    // ---------- 拒绝条件 ----------

    @Test
    void forceClose_shouldRejectOrderNotAudited() {
        PurchaseOrder order = order(StatusConstants.DRAFT, 10, 0);
        stubOrder(order);

        assertThatThrownBy(() -> service.forceClose(1L, "作废", OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅「已审核」的采购订单可以强制结单");

        assertThat(order.isForceClosed()).isFalse();
        verifyNoInteractions(supplierLedgerLock, auditPublisher);
    }

    @Test
    void forceClose_shouldRejectAlreadyForceClosedOrder() {
        PurchaseOrder order = order(StatusConstants.PURCHASE_COMPLETED, 10, 9);
        order.setForceClosed(true);
        stubOrder(order);

        assertThatThrownBy(() -> service.forceClose(1L, "作废", OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已强制结单");
    }

    @Test
    void forceClose_shouldRejectBlankReason() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 10, 9);
        stubOrder(order);

        assertThatThrownBy(() -> service.forceClose(1L, "   ", OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("强制结单原因不能为空");
        assertThatThrownBy(() -> service.forceClose(1L, null, OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("强制结单原因不能为空");
    }

    @Test
    void forceClose_shouldRejectOverlongReason() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 10, 9);
        stubOrder(order);

        assertThatThrownBy(() -> service.forceClose(1L, "废".repeat(256), OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 255 个字符");
    }

    @Test
    void forceClose_shouldRejectOrderWithoutRemainingQuantity() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 10, 10);
        stubOrder(order);
        when(purchaseInboundItemQueryService.findAllActiveBySourcePurchaseOrderItemIds(List.of(100L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.forceClose(1L, "作废", OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已无未入库件数");

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED);
        verify(supplierLedgerLock, never()).lock(any(), any());
    }

    @Test
    void forceClose_shouldRejectWhenDraftInboundReferencesOrder() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 10, 9);
        stubOrder(order);
        PurchaseInbound draft = new PurchaseInbound();
        draft.setStatus(StatusConstants.DRAFT);
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setPurchaseInbound(draft);
        when(purchaseInboundItemQueryService.findAllActiveBySourcePurchaseOrderItemIds(List.of(100L)))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> service.forceClose(1L, "作废", OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在未审核的采购入库单");

        assertThat(order.isForceClosed()).isFalse();
    }

    @Test
    void forceClose_shouldRejectWhenDirectSalesExceedsReceived() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 10, 9);
        stubOrder(order);
        when(purchaseInboundItemQueryService.findAllActiveBySourcePurchaseOrderItemIds(List.of(100L)))
                .thenReturn(List.of());
        org.mockito.Mockito.doThrow(new BusinessException(
                        com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                        "来源采购明细 100 的历史直连销售数量超过最终入库量"))
                .when(directSalesCapacityGuard).assertCovered(List.of(100L));

        assertThatThrownBy(() -> service.forceClose(1L, "作废", OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直连销售数量超过最终入库量");
    }

    @Test
    void forceClose_shouldRejectOrderWithoutSupplierOrSettlementCompany() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 10, 9);
        order.setSettlementCompanyId(null);
        stubOrder(order);
        when(purchaseInboundItemQueryService.findAllActiveBySourcePurchaseOrderItemIds(List.of(100L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.forceClose(1L, "作废", OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少供应商或结算主体身份");
    }

    // ---------- 撤销结单 ----------

    @Test
    void cancelForceClose_shouldClearTrailAndReopenOrder() {
        PurchaseOrder order = order(StatusConstants.PURCHASE_COMPLETED, 10, 9);
        order.setForceClosed(true);
        order.setForceCloseReason("剩余 1 件报废");
        order.setForceCloseRemainingQuantity(1);
        order.setForceClosedBy(9L);
        order.setForceClosedName("系统管理员");
        order.setForceClosedAt(java.time.LocalDateTime.now());
        stubOrder(order);
        when(accountQuery.findById(9L)).thenReturn(Optional.of(
                new AccountQuery.AccountSnapshot(9L, "admin_prod", "系统管理员", null)));

        service.cancelForceClose(1L, OPERATOR);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED);
        assertThat(order.isForceClosed()).isFalse();
        assertThat(order.getForceCloseReason()).isNull();
        assertThat(order.getForceCloseRemainingQuantity()).isNull();
        assertThat(order.getForceClosedBy()).isNull();
        assertThat(order.getForceClosedName()).isNull();
        assertThat(order.getForceClosedAt()).isNull();
        ArgumentCaptor<String> remark = ArgumentCaptor.forClass(String.class);
        verify(auditPublisher).publish(
                org.mockito.ArgumentMatchers.eq(order),
                org.mockito.ArgumentMatchers.eq("PURCHASE_ORDER_FORCE_CLOSE_CANCELLED"),
                org.mockito.ArgumentMatchers.eq("撤销强制结单"),
                remark.capture()
        );
        assertThat(remark.getValue()).contains("恢复未入库 1 件").contains("剩余 1 件报废");
    }

    @Test
    void cancelForceClose_shouldRejectOrderNotForceClosed() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 10, 9);
        stubOrder(order);

        assertThatThrownBy(() -> service.cancelForceClose(1L, OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是强制结单");
    }

    @Test
    void cancelForceClose_shouldRejectWhenStatusAlreadyLeft() {
        PurchaseOrder order = order(StatusConstants.AUDITED, 10, 9);
        order.setForceClosed(true);
        stubOrder(order);

        assertThatThrownBy(() -> service.cancelForceClose(1L, OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法撤销强制结单");
    }

    @Test
    void forceClose_shouldRejectMissingOrder() {
        when(purchaseOrderRepository.findByIdAndDeletedFlagFalse(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forceClose(404L, "作废", OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购订单不存在");
    }
}
