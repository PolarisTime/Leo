package com.leo.erp.purchase.order.service;

import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderResponseAssemblerTest {

    @Mock
    private PurchaseOrderMapper mapper;

    @Mock
    private PurchaseOrderAvailabilityService availabilityService;

    @Mock
    private DocumentChargeItemService documentChargeItemService;

    private PurchaseOrderResponseAssembler assembler() {
        return new PurchaseOrderResponseAssembler(mapper, availabilityService, documentChargeItemService);
    }

    private PurchaseOrder order(boolean forceClosed) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-1");
        order.setStatus("完成采购");
        order.setForceClosed(forceClosed);
        if (forceClosed) {
            order.setForceCloseReason("剩余 1 件报废");
            order.setForceCloseRemainingQuantity(1);
            order.setForceClosedBy(9L);
            order.setForceClosedName("系统管理员");
            order.setForceClosedAt(LocalDateTime.of(2026, 10, 9, 13, 50, 43));
        }
        order.setItems(List.of());
        return order;
    }

    private void stubBaseResponse(PurchaseOrder order) {
        when(mapper.toResponse(order)).thenReturn(new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, order.getStatus(), false, null, List.of(), List.of()
        ));
    }

    /**
     * 列表走 mapper + withForceClose, 详情走显式构造: 两条路径都必须带出强制结单留痕,
     * 否则列表页看不出这单是"剩余量作废"结掉的。
     */
    @Test
    void toSummaryResponse_shouldCarryForceCloseTrail() {
        PurchaseOrder order = order(true);
        stubBaseResponse(order);

        PurchaseOrderResponse response = assembler().toSummaryResponse(order);

        assertThat(response.forceClose()).isNotNull();
        assertThat(response.forceClose().reason()).isEqualTo("剩余 1 件报废");
        assertThat(response.forceClose().remainingQuantity()).isEqualTo(1);
        assertThat(response.forceClose().operatorId()).isEqualTo(9L);
        assertThat(response.forceClose().operatorName()).isEqualTo("系统管理员");
        assertThat(response.forceClose().closedAt()).isEqualTo(LocalDateTime.of(2026, 10, 9, 13, 50, 43));
    }

    @Test
    void toDetailResponse_shouldCarryForceCloseTrail() {
        PurchaseOrder order = order(true);
        stubBaseResponse(order);
        when(availabilityService.loadInboundAllocatedQuantityMap(order)).thenReturn(Map.of());
        when(availabilityService.loadSalesAllocatedQuantityMap(order)).thenReturn(Map.of());

        PurchaseOrderResponse response = assembler().toDetailResponse(order);

        assertThat(response.forceClose()).isNotNull();
        assertThat(response.forceClose().reason()).isEqualTo("剩余 1 件报废");
    }

    @Test
    void toSummaryResponse_shouldExposeNullForceCloseWhenNotForceClosed() {
        PurchaseOrder order = order(false);
        stubBaseResponse(order);

        assertThat(assembler().toSummaryResponse(order).forceClose()).isNull();
    }

    private PurchaseOrderItemResponse item(Integer remainingQuantity) {
        return new PurchaseOrderItemResponse(
                1L,
                1,
                "M-1",
                "品牌",
                "品类",
                "材质",
                "规格",
                null,
                "吨",
                "仓库",
                "B-1",
                remainingQuantity,
                0,
                BigDecimal.ZERO,
                10,
                "件",
                BigDecimal.ONE,
                1,
                BigDecimal.TEN,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN
        );
    }

    @Test
    void totalRemainingQuantity_shouldSumRemainingAcrossLines() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(2), item(3)))).isEqualTo(5);
    }

    @Test
    void totalRemainingQuantity_shouldReturnZeroForNullOrEmptyOrAllZero() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(null)).isZero();
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(List.of())).isZero();
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(0), item(0)))).isZero();
    }

    /** 负值（异常数据）按 0 计，不得拉低总和。 */
    @Test
    void totalRemainingQuantity_shouldClampNegativeLinesToZero() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(4), item(-3)))).isEqualTo(4);
    }

    @Test
    void totalRemainingQuantity_shouldIgnoreNullRemainingAndNullEntries() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(null), item(5)))).isEqualTo(5);
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                java.util.Arrays.asList(item(3), null))).isEqualTo(3);
    }

    /** 总量超过 int 上界时收敛到 Integer.MAX_VALUE，避免回绕为负数。 */
    @Test
    void totalRemainingQuantity_shouldSaturateOnOverflow() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(Integer.MAX_VALUE), item(Integer.MAX_VALUE))))
                .isEqualTo(Integer.MAX_VALUE);
    }

    /** 便捷构造默认不带实际货值与差额，交由 applyReferenceFlags 链路补齐。 */
    @Test
    void convenienceConstructor_shouldDefaultDifferenceFieldsToNull() {
        PurchaseOrderResponse response = new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, "已审核", false, null, List.of()
        );

        assertThat(response.totalActualAmount()).isNull();
        assertThat(response.totalAmountDifference()).isNull();
        assertThat(response.totalRemainingQuantity()).isNull();
        assertThat(response.totalReceivedQuantity()).isNull();
    }

    /** applyReceivedQuantity 仅替换已入库件数，并透传 null。 */
    @Test
    void applyReceivedQuantity_shouldReplaceOnlyReceivedQuantity() {
        PurchaseOrderResponse base = new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, "已审核", false, null, List.of()
        ).applyTotalRemainingQuantity(7);

        PurchaseOrderResponse applied = base.applyReceivedQuantity(3);

        assertThat(applied.totalReceivedQuantity()).isEqualTo(3);
        assertThat(applied.totalRemainingQuantity()).isEqualTo(7);

        assertThat(base.applyReceivedQuantity(null).totalReceivedQuantity()).isNull();
    }

    /** applyDifference 仅替换实际货值与差额，保留既有未入库件数。 */
    @Test
    void applyDifference_shouldReplaceOnlyDifferenceFields() {
        PurchaseOrderResponse base = new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, "已审核", false, null, List.of()
        ).applyTotalRemainingQuantity(7);

        PurchaseOrderResponse applied = base.applyDifference(
                new BigDecimal("1200.50"), new BigDecimal("-200.50"));

        assertThat(applied.totalActualAmount()).isEqualByComparingTo("1200.50");
        assertThat(applied.totalAmountDifference()).isEqualByComparingTo("-200.50");
        assertThat(applied.totalRemainingQuantity()).isEqualTo(7);
    }

    /** applyDifference 透传 null 值。 */
    @Test
    void applyDifference_shouldPassthroughNulls() {
        PurchaseOrderResponse base = new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, "已审核", false, null, List.of()
        );

        PurchaseOrderResponse applied = base.applyDifference(null, null);

        assertThat(applied.totalActualAmount()).isNull();
        assertThat(applied.totalAmountDifference()).isNull();
    }
}
