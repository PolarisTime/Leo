package com.leo.erp.purchase.refund.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseRefundCalculatorTest {

    private final PurchaseRefundCalculator calculator = new PurchaseRefundCalculator();

    @Test
    void shouldRefundMeasuredWeightDifferenceWhenOrderedQuantityWasFullyReceived() {
        PurchaseOrderItem sourceItem = sourceItem(101L, 1, 18, "36.00000000", "3250.00");
        PurchaseOrder order = order(sourceItem);
        PurchaseInboundItem inboundItem = inboundItem(
                sourceItem,
                18,
                "36.00000000",
                "35.23800000",
                StatusConstants.AUDITED
        );

        PurchaseRefundCalculator.Calculation result = calculator.calculate(
                order,
                List.of(inboundItem)
        );

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().quantity()).isZero();
        assertThat(result.lines().getFirst().weightTon()).isEqualByComparingTo("0.76200000");
        assertThat(result.lines().getFirst().amount()).isEqualByComparingTo("2476.50");
        assertThat(result.totalQuantity()).isZero();
        assertThat(result.totalWeight()).isEqualByComparingTo("0.76200000");
        assertThat(result.totalAmount()).isEqualByComparingTo("2476.50");
        assertThat(result.fullyInbound()).isTrue();
    }

    @Test
    void shouldOnlyCountAuditedOrCompletedInboundAndFallbackToTheoreticalInboundWeight() {
        PurchaseOrderItem sourceItem = sourceItem(101L, 1, 10, "20.00000000", "3000.00");
        PurchaseOrder order = order(sourceItem);

        PurchaseRefundCalculator.Calculation result = calculator.calculate(
                order,
                List.of(
                        inboundItem(sourceItem, 3, "6.00000000", null, StatusConstants.AUDITED),
                        inboundItem(sourceItem, 2, "4.00000000", "4.00000000", StatusConstants.INBOUND_COMPLETED),
                        inboundItem(sourceItem, 5, "10.00000000", "10.00000000", StatusConstants.DRAFT)
                )
        );

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().quantity()).isEqualTo(5);
        assertThat(result.lines().getFirst().weightTon()).isEqualByComparingTo("10.00000000");
        assertThat(result.lines().getFirst().amount()).isEqualByComparingTo("30000.00");
        assertThat(result.fullyInbound()).isFalse();
    }

    @Test
    void shouldClampOverInboundQuantityAndWeightAtZero() {
        PurchaseOrderItem sourceItem = sourceItem(101L, 1, 10, "20.00000000", "3000.00");
        PurchaseOrder order = order(sourceItem);

        PurchaseRefundCalculator.Calculation result = calculator.calculate(
                order,
                List.of(inboundItem(
                        sourceItem,
                        12,
                        "25.00000000",
                        "25.00000000",
                        StatusConstants.AUDITED
                ))
        );

        assertThat(result.lines()).isEmpty();
        assertThat(result.hasPositiveRefund()).isFalse();
        assertThat(result.totalWeight()).isEqualByComparingTo("0.00000000");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(result.fullyInbound()).isTrue();
    }

    @Test
    void shouldRebuildOriginalPaymentBasisWhenOrderWeightAndAmountWereWrittenBack() {
        PurchaseOrderItem sourceItem = sourceItem(101L, 1, 18, "35.23800000", "3250.00");
        sourceItem.setAmount(new BigDecimal("114523.50"));

        PurchaseRefundCalculator.Calculation result = calculator.calculate(
                order(sourceItem),
                List.of(inboundItem(
                        sourceItem,
                        18,
                        "36.00000000",
                        "35.23800000",
                        StatusConstants.AUDITED
                ))
        );

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().weightTon()).isEqualByComparingTo("0.76200000");
        assertThat(result.lines().getFirst().amount()).isEqualByComparingTo("2476.50");
        assertThat(result.totalAmount()).isEqualByComparingTo("2476.50");
    }

    @Test
    void shouldKeepZeroRefundAmountWhenRoundedInboundSettlementAlreadyEqualsOrderBasis() {
        PurchaseOrderItem sourceItem = sourceItem(101L, 1, 1, "0.00500000", "1.00");
        sourceItem.setPieceWeightTon(new BigDecimal("0.00500000"));

        PurchaseRefundCalculator.Calculation result = calculator.calculate(
                order(sourceItem),
                List.of(inboundItem(
                        sourceItem,
                        1,
                        "0.00500000",
                        "0.00400000",
                        StatusConstants.AUDITED
                ))
        );

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().weightTon()).isEqualByComparingTo("0.00100000");
        assertThat(result.lines().getFirst().amount()).isEqualByComparingTo("0.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldCloseCentDifferenceAgainstRoundedOrderAndInboundSettlementAmounts() {
        PurchaseOrderItem sourceItem = sourceItem(101L, 1, 1, "0.00500000", "1.00");
        sourceItem.setPieceWeightTon(new BigDecimal("0.00500000"));
        PurchaseInboundItem inboundItem = inboundItem(
                sourceItem,
                1,
                "0.00400000",
                "0.00400000",
                StatusConstants.AUDITED
        );
        inboundItem.setAmount(new BigDecimal("0.00"));
        inboundItem.setWeightAdjustmentAmount(new BigDecimal("0.00"));

        PurchaseRefundCalculator.Calculation result = calculator.calculate(
                order(sourceItem),
                List.of(inboundItem)
        );

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().weightTon()).isEqualByComparingTo("0.00100000");
        assertThat(result.lines().getFirst().amount()).isEqualByComparingTo("0.01");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.01");
    }

    private PurchaseOrder order(PurchaseOrderItem... items) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setStatus(StatusConstants.AUDITED);
        order.setItems(new java.util.ArrayList<>(List.of(items)));
        for (PurchaseOrderItem item : items) {
            item.setPurchaseOrder(order);
        }
        return order;
    }

    private PurchaseOrderItem sourceItem(Long id,
                                         int lineNo,
                                         int quantity,
                                         String weightTon,
                                         String unitPrice) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setLineNo(lineNo);
        item.setMaterialCode("MAT-001");
        item.setBrand("新澎辉");
        item.setCategory("盘螺");
        item.setMaterial("HRB400E");
        item.setSpec("8");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setQuantity(quantity);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.00000000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal(weightTon));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setAmount(item.getWeightTon().multiply(item.getUnitPrice()));
        return item;
    }

    private PurchaseInboundItem inboundItem(PurchaseOrderItem sourceItem,
                                            int quantity,
                                            String theoreticalWeight,
                                            String weighedWeight,
                                            String status) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setStatus(status);
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setSourcePurchaseOrderItemId(sourceItem.getId());
        item.setPurchaseInbound(inbound);
        item.setQuantity(quantity);
        item.setWeightTon(new BigDecimal(theoreticalWeight));
        item.setWeighWeightTon(weighedWeight == null ? null : new BigDecimal(weighedWeight));
        item.setAmount(TradeItemCalculator.calculateAmount(item.getWeightTon(), sourceItem.getUnitPrice()));
        BigDecimal weightAdjustment = item.getWeighWeightTon() == null
                ? BigDecimal.ZERO
                : item.getWeighWeightTon().subtract(item.getWeightTon());
        item.setWeightAdjustmentAmount(
                TradeItemCalculator.calculateAmount(weightAdjustment, sourceItem.getUnitPrice())
        );
        return item;
    }

}
