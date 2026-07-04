package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SalesOrderSourceAllocationServiceTest {

    private final SalesOrderSourceAllocationService service = new SalesOrderSourceAllocationService(
            mock(PurchaseItemQueryAppService.class),
            mock(SalesOrderItemRepository.class)
    );

    @Test
    void shouldCollectInboundNoWithoutBlankPurchaseOrderNo() {
        SalesOrderSourceContext context = context(
                Map.of(10L, inboundRecord(10L, "PI-001", null)),
                Map.of()
        );

        var resolved = service.resolveSourceInbound(request(10L, null, 1), context);

        assertThat(resolved).isNotNull();
        assertThat(context.sourceInboundNos()).containsExactly("PI-001");
        assertThat(context.sourcePurchaseOrderNos()).isEmpty();
    }

    @Test
    void shouldCollectPurchaseOrderNo() {
        SalesOrderSourceContext context = context(
                Map.of(),
                Map.of(20L, purchaseOrderRecord(20L, 10, "PO-001"))
        );

        var resolved = service.resolveSourcePurchaseOrder(request(null, 20L, 1), context);

        assertThat(resolved).isNotNull();
        assertThat(context.sourcePurchaseOrderNos()).containsExactly("PO-001");
    }

    @Test
    void shouldRejectLineWithBothInboundAndPurchaseOrderSources() {
        SalesOrderSourceContext context = context(Map.of(), Map.of());

        assertThatThrownBy(() -> service.validateLine(request(10L, 20L, 1), 3, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第3行来源采购入库明细和来源采购订单明细不能同时填写");
    }

    @Test
    void shouldAllowNullRequestedAndSourceQuantityWhenNothingIsAllocated() {
        SalesOrderSourceContext context = context(
                Map.of(),
                Map.of(20L, purchaseOrderRecord(20L, null, "PO-001"))
        );

        service.validateLine(request(null, 20L, null), 1, context);
    }

    @Test
    void shouldMergeSourceAllocationsAndScaleWeight() {
        SalesOrderSourceAllocation merged = SalesOrderSourceAllocation.merge(
                new SalesOrderSourceAllocation(2, new BigDecimal("0.123456784")),
                new SalesOrderSourceAllocation(3, new BigDecimal("0.100000001"))
        );

        assertThat(merged.quantity()).isEqualTo(5);
        assertThat(merged.weightTon()).isEqualByComparingTo("0.22345679");
        assertThat(merged.weightTon().scale()).isEqualTo(8);
    }

    private SalesOrderSourceContext context(
            Map<Long, PurchaseItemQueryAppService.SourceInboundItemRecord> inboundItems,
            Map<Long, PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord> purchaseOrderItems
    ) {
        return new SalesOrderSourceContext(
                List.copyOf(inboundItems.keySet()),
                List.copyOf(purchaseOrderItems.keySet()),
                inboundItems,
                purchaseOrderItems,
                Map.of(),
                Map.of(),
                Map.of(),
                new HashMap<>(),
                new HashMap<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>()
        );
    }

    private SalesOrderItemRequest request(Long sourceInboundItemId, Long sourcePurchaseOrderItemId, Integer quantity) {
        return new SalesOrderItemRequest(
                null,
                "M1",
                "宝钢",
                "盘螺",
                "HRB400",
                "8",
                null,
                "吨",
                sourceInboundItemId,
                sourcePurchaseOrderItemId,
                "一号库",
                "B1",
                quantity,
                "件",
                new BigDecimal("0.100"),
                1,
                new BigDecimal("0.100"),
                new BigDecimal("4000.00"),
                new BigDecimal("400.00")
        );
    }

    private PurchaseItemQueryAppService.SourceInboundItemRecord inboundRecord(
            Long id,
            String inboundNo,
            String purchaseOrderNo
    ) {
        return new PurchaseItemQueryAppService.SourceInboundItemRecord(
                id,
                inboundNo,
                StatusConstants.AUDITED,
                purchaseOrderNo,
                10,
                new BigDecimal("1.000"),
                "宝钢",
                "HRB400",
                "8",
                "M1",
                "盘螺",
                "吨",
                "一号库",
                "B1",
                null,
                null
        );
    }

    private PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord purchaseOrderRecord(
            Long id,
            Integer quantity,
            String orderNo
    ) {
        return new PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord(
                id,
                quantity,
                new BigDecimal("1.000"),
                new BigDecimal("0.100"),
                orderNo,
                StatusConstants.AUDITED,
                "宝钢",
                "HRB400",
                "8",
                "M1",
                "盘螺",
                "吨",
                "一号库",
                "B1",
                null,
                null
        );
    }
}
