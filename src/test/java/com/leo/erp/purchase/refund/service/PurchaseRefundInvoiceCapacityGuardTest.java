package com.leo.erp.purchase.refund.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseRefundInvoiceCapacityGuardTest {

    @Test
    void shouldAllowInvoiceAndRefundAtExactOriginalCapacity() {
        InvoiceReceiptRepository repository = mock(InvoiceReceiptRepository.class);
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(11L), null))
                .thenReturn(List.of(summary(11L, 7L, "14.00000000", "1400.00")));
        PurchaseRefundInvoiceCapacityGuard guard = new PurchaseRefundInvoiceCapacityGuard(repository);

        assertThatCode(() -> guard.assertRefundFits(
                sourceOrder(),
                calculation(3, "6.00000000", "600.00")
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWhenExistingInvoiceAndRefundExceedQuantity() {
        InvoiceReceiptRepository repository = mock(InvoiceReceiptRepository.class);
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(11L), null))
                .thenReturn(List.of(summary(11L, 8L, "14.00000000", "1400.00")));
        PurchaseRefundInvoiceCapacityGuard guard = new PurchaseRefundInvoiceCapacityGuard(repository);

        assertThatThrownBy(() -> guard.assertRefundFits(
                sourceOrder(),
                calculation(3, "6.00000000", "600.00")
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("已收票数量与退款数量合计超过采购订单");
    }

    @Test
    void shouldRejectWhenExistingInvoiceAndRefundExceedWeight() {
        InvoiceReceiptRepository repository = mock(InvoiceReceiptRepository.class);
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(11L), null))
                .thenReturn(List.of(summary(11L, 7L, "15.00000000", "1400.00")));
        PurchaseRefundInvoiceCapacityGuard guard = new PurchaseRefundInvoiceCapacityGuard(repository);

        assertThatThrownBy(() -> guard.assertRefundFits(
                sourceOrder(),
                calculation(3, "6.00000000", "600.00")
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("已收票吨位与退款吨位合计超过采购订单");
    }

    @Test
    void shouldRejectWhenExistingInvoiceAndRefundExceedAmount() {
        InvoiceReceiptRepository repository = mock(InvoiceReceiptRepository.class);
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(11L), null))
                .thenReturn(List.of(summary(11L, 7L, "14.00000000", "1500.00")));
        PurchaseRefundInvoiceCapacityGuard guard = new PurchaseRefundInvoiceCapacityGuard(repository);

        assertThatThrownBy(() -> guard.assertRefundFits(
                sourceOrder(),
                calculation(3, "6.00000000", "600.00")
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("已收票金额与退款金额合计超过采购订单");
    }

    private PurchaseOrder sourceOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(11L);
        item.setPurchaseOrder(order);
        item.setQuantity(10);
        item.setPieceWeightTon(new BigDecimal("2.00000000"));
        item.setUnitPrice(new BigDecimal("100.00"));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private PurchaseRefundCalculator.Calculation calculation(int quantity,
                                                              String weight,
                                                              String amount) {
        PurchaseOrderItem item = sourceOrder().getItems().getFirst();
        return new PurchaseRefundCalculator.Calculation(
                List.of(new PurchaseRefundCalculator.Line(
                        item,
                        quantity,
                        new BigDecimal(weight),
                        new BigDecimal(amount)
                )),
                quantity,
                new BigDecimal(weight),
                new BigDecimal(amount),
                false
        );
    }

    private InvoiceReceiptRepository.SourceAllocationSummary summary(Long itemId,
                                                                      Long quantity,
                                                                      String weight,
                                                                      String amount) {
        return new InvoiceReceiptRepository.SourceAllocationSummary() {
            @Override
            public Long getSourcePurchaseOrderItemId() {
                return itemId;
            }

            @Override
            public Long getTotalQuantity() {
                return quantity;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return new BigDecimal(weight);
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal(amount);
            }
        };
    }
}
