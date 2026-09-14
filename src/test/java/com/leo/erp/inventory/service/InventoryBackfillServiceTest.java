package com.leo.erp.inventory.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.inventory.api.InventoryTransactionCommand;
import com.leo.erp.inventory.api.InventoryTransactionInput;
import com.leo.erp.inventory.repository.InventoryTransactionRepository;
import com.leo.erp.inventory.web.dto.InventoryBackfillResponse;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryBackfillService 幂等、状态过滤与统计行为测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryBackfillServiceTest {

    @Mock
    private PurchaseInboundRepository purchaseInboundRepository;

    @Mock
    private SalesOutboundRepository salesOutboundRepository;

    @Mock
    private SalesReturnRepository salesReturnRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private InventoryTransactionLockService lockService;

    @Mock
    private InventoryTransactionCommand inventoryCommand;

    @InjectMocks
    private InventoryBackfillService service;

    @Test
    void backfill_shouldSkipSourcesWithActiveTransactionAndNotRecord() {
        PurchaseInbound inbound = purchaseInbound(5L, LocalDate.of(2026, 9, 1), purchaseItem(11L, 100L, 5));
        when(purchaseInboundRepository.findAllByStatusInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(inbound));
        when(transactionRepository
                .existsBySourceDocumentTypeAndSourceItemIdAndTransactionTypeAndDeletedFlagFalse(
                        "PURCHASE_INBOUND", 11L, "PURCHASE_IN")).thenReturn(true);

        InventoryBackfillResponse response = service.backfill();

        assertThat(response.purchaseInCreated()).isZero();
        assertThat(response.salesOutCreated()).isZero();
        assertThat(response.salesReturnCreated()).isZero();
        assertThat(response.skipped()).isEqualTo(1);
        verify(inventoryCommand, never()).recordPurchaseIn(any());
        verify(inventoryCommand, never()).recordSalesOut(any());
        verify(inventoryCommand, never()).recordSalesReturnIn(any());
    }

    @Test
    void backfill_shouldQueryOnlyPostedDocuments() {
        service.backfill();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(purchaseInboundRepository).findAllByStatusInAndDeletedFlagFalse(statusesCaptor.capture());
        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrder(StatusConstants.AUDITED, StatusConstants.INBOUND_COMPLETED);

        verify(salesOutboundRepository).findAllByStatusAndDeletedFlagFalse(StatusConstants.AUDITED);
        verify(salesReturnRepository).findByStatus(StatusConstants.AUDITED);
    }

    @Test
    void backfill_shouldCreateMissingTransactionsAndReportCounts() {
        PurchaseInbound inbound = purchaseInbound(5L, LocalDate.of(2026, 9, 1), purchaseItem(11L, 100L, 5));
        SalesOutbound outbound = salesOutbound(6L, LocalDate.of(2026, 9, 2),
                salesOutboundItem(21L, 100L, 2), salesOutboundItem(22L, 101L, 3));
        SalesReturn salesReturn = salesReturn(7L, LocalDate.of(2026, 9, 3), salesReturnItem(31L, 100L, 1));
        when(purchaseInboundRepository.findAllByStatusInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(inbound));
        when(salesOutboundRepository.findAllByStatusAndDeletedFlagFalse(anyString()))
                .thenReturn(List.of(outbound));
        when(salesReturnRepository.findByStatus(anyString()))
                .thenReturn(List.of(salesReturn));

        InventoryBackfillResponse response = service.backfill();

        assertThat(response.purchaseInCreated()).isEqualTo(1);
        assertThat(response.salesOutCreated()).isEqualTo(2);
        assertThat(response.salesReturnCreated()).isEqualTo(1);
        assertThat(response.skipped()).isZero();

        ArgumentCaptor<InventoryTransactionInput> captor = ArgumentCaptor.forClass(InventoryTransactionInput.class);
        verify(inventoryCommand).recordPurchaseIn(captor.capture());
        assertThat(captor.getValue().sourceDocumentType()).isEqualTo("PURCHASE_INBOUND");
        assertThat(captor.getValue().sourceDocumentId()).isEqualTo(5L);
        assertThat(captor.getValue().lines()).hasSize(1);
        verify(inventoryCommand).recordSalesOut(any());
        verify(inventoryCommand).recordSalesReturnIn(any());
        verify(lockService).lockBackfill();
    }

    @Test
    void backfill_shouldProcessDocumentsInBusinessDateOrder() {
        PurchaseInbound laterInbound = purchaseInbound(5L, LocalDate.of(2026, 9, 10), purchaseItem(11L, 100L, 5));
        SalesOutbound earlierOutbound = salesOutbound(6L, LocalDate.of(2026, 9, 1),
                salesOutboundItem(21L, 100L, 2));
        when(purchaseInboundRepository.findAllByStatusInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(laterInbound));
        when(salesOutboundRepository.findAllByStatusAndDeletedFlagFalse(anyString()))
                .thenReturn(List.of(earlierOutbound));

        service.backfill();

        InOrder order = inOrder(inventoryCommand);
        order.verify(inventoryCommand).recordSalesOut(any());
        order.verify(inventoryCommand).recordPurchaseIn(any());
    }

    @Test
    void backfill_shouldCountInvalidLinesAsSkipped() {
        PurchaseInbound inbound = purchaseInbound(5L, LocalDate.of(2026, 9, 1),
                purchaseItem(11L, 100L, 0), purchaseItem(12L, null, 5));
        when(purchaseInboundRepository.findAllByStatusInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(inbound));

        InventoryBackfillResponse response = service.backfill();

        assertThat(response.purchaseInCreated()).isZero();
        assertThat(response.skipped()).isEqualTo(2);
        verify(inventoryCommand, never()).recordPurchaseIn(any());
    }

    private PurchaseInbound purchaseInbound(long id, LocalDate date, PurchaseInboundItem... items) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(id);
        inbound.setInboundNo("PI" + id);
        inbound.setInboundDate(date);
        inbound.setWarehouseId(9L);
        inbound.setWarehouseName("库房A");
        inbound.setItems(new ArrayList<>(List.of(items)));
        return inbound;
    }

    private PurchaseInboundItem purchaseItem(long id, Long materialId, int quantity) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(id);
        item.setMaterialId(materialId);
        item.setMaterialCode(materialId == null ? null : "M" + materialId);
        item.setQuantity(quantity);
        item.setQuantityUnit("件");
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setWarehouseId(9L);
        item.setWarehouseName("库房A");
        return item;
    }

    private SalesOutbound salesOutbound(long id, LocalDate date, SalesOutboundItem... items) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(id);
        outbound.setOutboundNo("SO" + id);
        outbound.setOutboundDate(date);
        outbound.setWarehouseId(9L);
        outbound.setWarehouseName("库房A");
        outbound.setItems(new ArrayList<>(List.of(items)));
        return outbound;
    }

    private SalesOutboundItem salesOutboundItem(long id, Long materialId, int quantity) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(id);
        item.setMaterialId(materialId);
        item.setMaterialCode("M" + materialId);
        item.setQuantity(quantity);
        item.setQuantityUnit("件");
        item.setUnitPrice(new BigDecimal("120.00"));
        item.setWarehouseId(9L);
        item.setWarehouseName("库房A");
        return item;
    }

    private SalesReturn salesReturn(long id, LocalDate date, SalesReturnItem... items) {
        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setId(id);
        salesReturn.setReturnNo("SR" + id);
        salesReturn.setReturnDate(date);
        salesReturn.setWarehouseId(9L);
        salesReturn.setWarehouseName("库房A");
        salesReturn.setItems(new ArrayList<>(List.of(items)));
        return salesReturn;
    }

    private SalesReturnItem salesReturnItem(long id, Long materialId, int quantity) {
        SalesReturnItem item = new SalesReturnItem();
        item.setId(id);
        item.setMaterialId(materialId);
        item.setMaterialCode("M" + materialId);
        item.setQuantity(quantity);
        item.setQuantityUnit("件");
        item.setUnitPrice(new BigDecimal("150.00"));
        item.setWarehouseId(9L);
        item.setWarehouseName("库房A");
        return item;
    }
}
