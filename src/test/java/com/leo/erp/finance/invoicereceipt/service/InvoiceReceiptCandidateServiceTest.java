package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceReceiptCandidateServiceTest {

    @Test
    void shouldReturnRefundAdjustedSnapshotExcludingCurrentReceipt() {
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        InvoiceReceiptRepository invoiceReceiptRepository = mock(InvoiceReceiptRepository.class);
        InvoiceReceiptCandidateService service = new InvoiceReceiptCandidateService(
                purchaseOrderRepository,
                invoiceReceiptRepository,
                mock(ResourceRecordAccessGuard.class)
        );
        PurchaseOrder order = sourceOrder();
        InvoiceReceiptRepository.SourceAllocationSummary receipt = summary(
                201L,
                3L,
                "6.00000000",
                "18000.00"
        );
        InvoiceReceiptRepository.SourceAllocationSummary refund = summary(
                201L,
                2L,
                "4.00000000",
                "12000.00"
        );
        when(purchaseOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(201L), 9002L))
                .thenReturn(List.of(receipt));
        when(invoiceReceiptRepository.summarizeAuditedRefundBySourcePurchaseOrderItemIds(List.of(201L)))
                .thenReturn(List.of(refund));

        var result = service.sourceCandidates(
                PageQuery.of(0, 15, null, null),
                PageFilter.of(null, "供应商A", 88L, null, null, null)
                        .withIdentity(null, null, 701L, null, 9002L)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).singleElement().satisfies(candidate -> {
            assertThat(candidate.supplierId()).isEqualTo(701L);
            assertThat(candidate.items()).singleElement().satisfies(item -> {
                assertThat(item.id()).isEqualTo(201L);
                assertThat(item.quantity()).isEqualTo(5);
                assertThat(item.weightTon()).isEqualByComparingTo("10.00000000");
                assertThat(item.amount()).isEqualByComparingTo("30000.00");
            });
        });
        verify(invoiceReceiptRepository)
                .summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(201L), 9002L);
    }

    private InvoiceReceiptRepository.SourceAllocationSummary summary(
            Long itemId,
            Long quantity,
            String weight,
            String amount
    ) {
        InvoiceReceiptRepository.SourceAllocationSummary summary =
                mock(InvoiceReceiptRepository.SourceAllocationSummary.class);
        when(summary.getSourcePurchaseOrderItemId()).thenReturn(itemId);
        when(summary.getTotalQuantity()).thenReturn(quantity);
        when(summary.getTotalWeightTon()).thenReturn(new BigDecimal(weight));
        when(summary.getTotalAmount()).thenReturn(new BigDecimal(amount));
        return summary;
    }

    private PurchaseOrder sourceOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(2L);
        order.setOrderNo("PO-001");
        order.setSupplierId(701L);
        order.setSupplierCode("SUP-001");
        order.setSupplierName("供应商A");
        order.setSettlementCompanyId(88L);
        order.setSettlementCompanyName("结算主体A");
        order.setOrderDate(LocalDateTime.of(2026, 7, 13, 10, 0));
        order.setStatus(StatusConstants.PURCHASE_COMPLETED);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setPurchaseOrder(order);
        item.setLineNo(1);
        item.setQuantity(10);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.00000000"));
        item.setWeightTon(new BigDecimal("20.00000000"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("60000.00"));
        order.getItems().add(item);
        return order;
    }
}
