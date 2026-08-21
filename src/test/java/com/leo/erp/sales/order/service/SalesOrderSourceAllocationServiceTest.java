package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.api.PurchaseItemQueryAppService;
import com.leo.erp.purchase.api.PurchaseItemQueryAppService.SourceInboundItemRecord;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SalesOrderSourceAllocationService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderSourceAllocationServiceTest {

    @Mock
    private PurchaseItemQueryAppService purchaseItemQueryAppService;

    @Mock
    private SalesOrderItemRepository salesOrderItemRepository;

    @InjectMocks
    private SalesOrderSourceAllocationService service;

    private SourceInboundItemRecord inbound(Long id, String inboundStatus, String purchaseOrderStatus, Integer quantity) {
        return new SourceInboundItemRecord(
                id, "IB001", inboundStatus, "PO001", purchaseOrderStatus, quantity,
                new BigDecimal("12.500"), "品牌A", "螺纹钢", "HRB400", "M001", "型钢", "吨",
                "库房A", "B001", 30L, "结算公司A", 500L, 1L, "b001", "12m", "件",
                new BigDecimal("1.250"), 100);
    }

    private SalesOrderItemRequest itemRequest(Long inboundId, Long purchaseOrderId, Integer quantity) {
        return new SalesOrderItemRequest(
                null, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                inboundId, purchaseOrderId, 1L, "库房A", "B001", quantity, "件",
                new BigDecimal("1.250"), 100, new BigDecimal("12.500"), new BigDecimal("4000"), null);
    }

    private SalesOrderRequest request(List<SalesOrderItemRequest> items) {
        return new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", null, null, items, false);
    }

    private void stubInbound(List<SourceInboundItemRecord> inbounds) {
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(any())).thenReturn(inbounds);
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(any(), any()))
                .thenReturn(List.of());
    }

    // ---------- prepareContext ----------

    @Test
    void prepareContext_shouldBuildForInboundSource() {
        stubInbound(List.of(inbound(1L, StatusConstants.AUDITED, StatusConstants.PURCHASE_COMPLETED, 10)));
        SalesOrderRequest request = request(List.of(itemRequest(1L, null, 5)));

        SalesOrderSourceContext context = service.prepareContext(request, null, List.of());

        assertThat(context.sourceInboundItemIds()).containsExactly(1L);
        assertThat(context.sourceInboundItemMap()).containsKey(1L);
    }

    // ---------- validateSourceShape ----------

    @Test
    void prepareContext_shouldRejectNoSource() {
        SalesOrderRequest request = request(List.of(itemRequest(null, null, 5)));

        assertThatThrownBy(() -> service.prepareContext(request, null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须且只能选择一个");
    }

    @Test
    void prepareContext_shouldRejectBothSources() {
        SalesOrderRequest request = request(List.of(itemRequest(1L, 2L, 5)));

        assertThatThrownBy(() -> service.prepareContext(request, null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须且只能选择一个");
    }

    @Test
    void prepareContext_shouldRejectDuplicateInboundSource() {
        SalesOrderRequest request = request(List.of(itemRequest(1L, null, 5), itemRequest(1L, null, 5)));

        assertThatThrownBy(() -> service.prepareContext(request, null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复导入同一采购入库明细");
    }

    @Test
    void prepareContext_shouldRejectNewPurchaseDirectSource() {
        // 采购订单直连来源已停用，新行不允许
        SalesOrderRequest request = request(List.of(itemRequest(null, 2L, 5)));

        assertThatThrownBy(() -> service.prepareContext(request, null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购订单直连来源已停用");
    }

    // ---------- validateLine ----------

    @Test
    void validateLine_shouldRejectMissingInbound() {
        // 来源入库不存在于 map
        stubInbound(List.of());
        SalesOrderSourceContext context = service.prepareContext(
                request(List.of(itemRequest(1L, null, 5))), null, List.of());

        assertThatThrownBy(() -> service.validateLine(itemRequest(1L, null, 5), 1, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购入库明细不存在");
    }

    @Test
    void validateLine_shouldRejectInboundNotAudited() {
        stubInbound(List.of(inbound(1L, StatusConstants.DRAFT, StatusConstants.PURCHASE_COMPLETED, 10)));
        SalesOrderSourceContext context = service.prepareContext(
                request(List.of(itemRequest(1L, null, 5))), null, List.of());

        assertThatThrownBy(() -> service.validateLine(itemRequest(1L, null, 5), 1, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未审核");
    }

    @Test
    void validateLine_shouldRejectPurchaseNotCompleted() {
        stubInbound(List.of(inbound(1L, StatusConstants.AUDITED, StatusConstants.AUDITED, 10)));
        SalesOrderSourceContext context = service.prepareContext(
                request(List.of(itemRequest(1L, null, 5))), null, List.of());

        assertThatThrownBy(() -> service.validateLine(itemRequest(1L, null, 5), 1, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未完成采购");
    }

    @Test
    void validateLine_shouldRejectQuantityExceeded() {
        stubInbound(List.of(inbound(1L, StatusConstants.AUDITED, StatusConstants.PURCHASE_COMPLETED, 10)));
        SalesOrderSourceContext context = service.prepareContext(
                request(List.of(itemRequest(1L, null, 5))), null, List.of());

        assertThatThrownBy(() -> service.validateLine(itemRequest(1L, null, 99), 1, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可关联数量不足");
    }

    @Test
    void validateLine_shouldPassWhenValid() {
        stubInbound(List.of(inbound(1L, StatusConstants.AUDITED, StatusConstants.PURCHASE_COMPLETED, 10)));
        SalesOrderSourceContext context = service.prepareContext(
                request(List.of(itemRequest(1L, null, 5))), null, List.of());

        service.validateLine(itemRequest(1L, null, 5), 1, context); // 不抛
    }

    // ---------- recordAllocation / resolveSourceInbound ----------

    @Test
    void recordAllocation_shouldMergeInboundAllocation() {
        stubInbound(List.of(inbound(1L, StatusConstants.AUDITED, StatusConstants.PURCHASE_COMPLETED, 10)));
        SalesOrderSourceContext context = service.prepareContext(
                request(List.of(itemRequest(1L, null, 5))), null, List.of());

        service.recordAllocation(itemRequest(1L, null, 5), new BigDecimal("6.250"), context);
        service.recordAllocation(itemRequest(1L, null, 3), new BigDecimal("3.750"), context);

        assertThat(context.requestInboundAllocatedMap().get(1L).quantity()).isEqualTo(8);
    }

    @Test
    void resolveSourceInbound_shouldCollectInboundAndPurchaseOrderNos() {
        stubInbound(List.of(inbound(1L, StatusConstants.AUDITED, StatusConstants.PURCHASE_COMPLETED, 10)));
        SalesOrderSourceContext context = service.prepareContext(
                request(List.of(itemRequest(1L, null, 5))), null, List.of());

        service.resolveSourceInbound(itemRequest(1L, null, 5), context);

        assertThat(context.sourceInboundNos()).contains("IB001");
        assertThat(context.sourcePurchaseOrderNos()).contains("PO001");
    }
}
