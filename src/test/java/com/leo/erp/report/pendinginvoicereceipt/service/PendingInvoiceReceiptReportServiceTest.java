package com.leo.erp.report.pendinginvoicereceipt.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.report.pendinginvoicereceipt.web.dto.PendingInvoiceReceiptReportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingInvoiceReceiptReportServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private InvoiceReceiptRepository invoiceReceiptRepository;

    @InjectMocks
    private PendingInvoiceReceiptReportService service;

    @Test
    void pageQueriesProgressOnlyForAccessiblePurchaseOrderItems() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(201L, "2.000", "200.00"),
                purchaseOrderItem(202L, "1.000", "80.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Sort.class))).thenReturn(List.of(order));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of(summary(201L, "1.250", "100.00")));

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, "orderNo", "asc"),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().getFirst().pendingInvoiceWeightTon()).isEqualByComparingTo("0.750");
        assertThat(page.getContent().getFirst().pendingInvoiceAmount()).isEqualByComparingTo("100.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> sourceItemIds = ArgumentCaptor.forClass(Collection.class);
        verify(invoiceReceiptRepository).summarizeAllocatedBySourcePurchaseOrderItemIds(sourceItemIds.capture(), isNull());
        assertThat(sourceItemIds.getValue()).containsExactly(201L, 202L);
        verify(purchaseOrderRepository, never()).findAllByDeletedFlagFalse();
        verify(invoiceReceiptRepository, never()).findAllByDeletedFlagFalse();
    }

    @Test
    void pageSkipsProgressQueryWhenNoAccessiblePurchaseOrderItemsExist() {
        when(purchaseOrderRepository.findAll(anySpecification(), any(Sort.class))).thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isZero();
        verify(invoiceReceiptRepository, never()).summarizeAllocatedBySourcePurchaseOrderItemIds(any(), any());
    }

    @SuppressWarnings("unchecked")
    private Specification<PurchaseOrder> anySpecification() {
        return any(Specification.class);
    }

    private PurchaseOrder purchaseOrder(PurchaseOrderItem... items) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(101L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setOrderDate(LocalDate.of(2026, 4, 26));
        order.setCreatedBy(1L);
        order.setDeletedFlag(Boolean.FALSE);
        order.getItems().addAll(List.of(items));
        for (PurchaseOrderItem item : items) {
            item.setPurchaseOrder(order);
        }
        return order;
    }

    private PurchaseOrderItem purchaseOrderItem(Long id, String weightTon, String amount) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setMaterialCode("M-" + id);
        item.setBrand("品牌A");
        item.setMaterial("材质A");
        item.setCategory("品类A");
        item.setSpec("规格A");
        item.setLength("9m");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setWeightTon(new BigDecimal(weightTon));
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setAmount(new BigDecimal(amount));
        return item;
    }

    private InvoiceReceiptRepository.SourceAllocationSummary summary(Long itemId, String weightTon, String amount) {
        return new InvoiceReceiptRepository.SourceAllocationSummary() {
            @Override
            public Long getSourcePurchaseOrderItemId() {
                return itemId;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return new BigDecimal(weightTon);
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal(amount);
            }
        };
    }
}
