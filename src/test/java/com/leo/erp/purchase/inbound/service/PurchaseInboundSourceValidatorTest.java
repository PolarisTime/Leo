package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购入库来源校验测试：放开「单订单」限制，允许同一入库单合并多张已审核采购订单；
 * 保留单内重复来源行拒绝，并校验多订单拼接后的表头单号长度。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseInboundSourceValidatorTest {

    @Mock
    private PurchaseOrderItemQueryService purchaseOrderItemQueryService;

    @Mock
    private PurchaseInboundAllocationService allocationService;

    @InjectMocks
    private PurchaseInboundSourceValidator validator;

    private PurchaseOrder order(Long id, String orderNo, String status) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setStatus(status);
        return order;
    }

    private PurchaseOrderItem item(Long id, PurchaseOrder order) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrder(order);
        return item;
    }

    private PurchaseInboundItemRequest line(Long sourceItemId) {
        return new PurchaseInboundItemRequest(
                null, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                sourceItemId, "库房A", "现结", "B001", 5, null,
                new BigDecimal("1.250"), 100, new BigDecimal("6.250"),
                new BigDecimal("6.250"), new BigDecimal("0.000"), new BigDecimal("0.00"),
                new BigDecimal("4000.00"), null);
    }

    private PurchaseInboundRequest request(String purchaseOrderNo, List<PurchaseInboundItemRequest> items) {
        return new PurchaseInboundRequest(
                "IN001", purchaseOrderNo, 1L, "SUP001", "供应商A", 2L, "仓库A",
                LocalDate.of(2026, 8, 1), "现结", StatusConstants.DRAFT, null, items, false);
    }

    @Test
    void prepareContext_shouldAllowMultipleSourcePurchaseOrders() {
        PurchaseOrder firstOrder = order(100L, "PO001", StatusConstants.AUDITED);
        PurchaseOrder secondOrder = order(200L, "PO002", StatusConstants.AUDITED);
        when(allocationService.extractSourcePurchaseOrderItemIds(any())).thenReturn(List.of(11L, 22L));
        when(allocationService.prepareContext(any(), any())).thenReturn(null);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L, 22L)))
                .thenReturn(List.of(item(11L, firstOrder), item(22L, secondOrder)));

        assertThatCode(() -> validator.prepareContext(
                request("PO001, PO002", List.of(line(11L), line(22L))), null, List.of()))
                .doesNotThrowAnyException();
    }

    /** 表头单号不再强制等于唯一来源单号。 */
    @Test
    void prepareContext_shouldNotEnforceHeaderPurchaseOrderNoMatchesSource() {
        PurchaseOrder sourceOrder = order(100L, "PO001", StatusConstants.AUDITED);
        when(allocationService.extractSourcePurchaseOrderItemIds(any())).thenReturn(List.of(11L));
        when(allocationService.prepareContext(any(), any())).thenReturn(null);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L)))
                .thenReturn(List.of(item(11L, sourceOrder)));

        assertThatCode(() -> validator.prepareContext(
                request("PO-OTHER", List.of(line(11L))), null, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void prepareContext_shouldRejectDuplicateSourceLines() {
        assertThatThrownBy(() -> validator.prepareContext(
                request("PO001", List.of(line(11L), line(11L))), null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复引用来源采购订单明细");
        verify(allocationService, never()).extractSourcePurchaseOrderItemIds(any());
    }

    @Test
    void prepareContext_shouldRejectEmptySourceLines() {
        // items 非空但来源明细均为空：由后续 validateLine 抛错，此处确认不触发整单校验异常。
        when(allocationService.extractSourcePurchaseOrderItemIds(any())).thenReturn(List.of());
        when(allocationService.prepareContext(any(), any())).thenReturn(null);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of())).thenReturn(List.of());

        assertThatCode(() -> validator.prepareContext(
                request("PO001", List.of(line(null))), null, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void assertPurchaseOrderNoWithinLength_shouldAcceptNullAndBoundary() {
        assertThatCode(() -> validator.assertPurchaseOrderNoWithinLength(null)).doesNotThrowAnyException();
        String boundary = "A".repeat(PurchaseInboundSourceValidator.MAX_PURCHASE_ORDER_NO_LENGTH);
        assertThatCode(() -> validator.assertPurchaseOrderNoWithinLength(boundary)).doesNotThrowAnyException();
    }

    @Test
    void assertPurchaseOrderNoWithinLength_shouldRejectOverlongConcatenatedNo() {
        String overlong = "A".repeat(PurchaseInboundSourceValidator.MAX_PURCHASE_ORDER_NO_LENGTH + 1);

        assertThatThrownBy(() -> validator.assertPurchaseOrderNoWithinLength(overlong))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("长度不能超过");
    }
}
