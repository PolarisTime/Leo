package com.leo.erp.allocation.appservice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PurchaseItemQueryAppServiceTest {

    @Test
    void shouldCreateSourceInboundItemRecord() {
        PurchaseItemQueryAppService.SourceInboundItemRecord record = new PurchaseItemQueryAppService.SourceInboundItemRecord(
                1L,
                "INB-001",
                "PO-001",
                100,
                new BigDecimal("5.5"),
                "BrandA",
                "MaterialB",
                "SpecC",
                "MC-001",
                "CategoryD",
                "UnitE",
                "WarehouseF",
                "BatchG"
        );

        assertThat(record.id()).isEqualTo(1L);
        assertThat(record.inboundNo()).isEqualTo("INB-001");
        assertThat(record.purchaseOrderNo()).isEqualTo("PO-001");
        assertThat(record.quantity()).isEqualTo(100);
        assertThat(record.weighWeightTon()).isEqualByComparingTo(new BigDecimal("5.5"));
        assertThat(record.brand()).isEqualTo("BrandA");
        assertThat(record.material()).isEqualTo("MaterialB");
        assertThat(record.spec()).isEqualTo("SpecC");
        assertThat(record.materialCode()).isEqualTo("MC-001");
        assertThat(record.category()).isEqualTo("CategoryD");
        assertThat(record.unit()).isEqualTo("UnitE");
        assertThat(record.warehouseName()).isEqualTo("WarehouseF");
        assertThat(record.batchNo()).isEqualTo("BatchG");
    }

    @Test
    void shouldCreateSourcePurchaseOrderItemRecord() {
        PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord record = new PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord(
                2L,
                200,
                new BigDecimal("10.25"),
                "PO-002",
                "BrandX",
                "MaterialY",
                "SpecZ",
                "MC-002",
                "CategoryW",
                "UnitV",
                "WarehouseU",
                "BatchT"
        );

        assertThat(record.id()).isEqualTo(2L);
        assertThat(record.quantity()).isEqualTo(200);
        assertThat(record.weightTon()).isEqualByComparingTo(new BigDecimal("10.25"));
        assertThat(record.orderNo()).isEqualTo("PO-002");
        assertThat(record.brand()).isEqualTo("BrandX");
        assertThat(record.material()).isEqualTo("MaterialY");
        assertThat(record.spec()).isEqualTo("SpecZ");
        assertThat(record.materialCode()).isEqualTo("MC-002");
        assertThat(record.category()).isEqualTo("CategoryW");
        assertThat(record.unit()).isEqualTo("UnitV");
        assertThat(record.warehouseName()).isEqualTo("WarehouseU");
        assertThat(record.batchNo()).isEqualTo("BatchT");
    }

    @Test
    void shouldCreatePieceWeightSummaryRecord() {
        PurchaseItemQueryAppService.PieceWeightSummary summary = new PurchaseItemQueryAppService.PieceWeightSummary(
                3L,
                new BigDecimal("15.75")
        );

        assertThat(summary.purchaseOrderItemId()).isEqualTo(3L);
        assertThat(summary.remainingWeight()).isEqualByComparingTo(new BigDecimal("15.75"));
    }

    @Test
    void shouldHandleNullValuesInSourceInboundItemRecord() {
        PurchaseItemQueryAppService.SourceInboundItemRecord record = new PurchaseItemQueryAppService.SourceInboundItemRecord(
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertThat(record.id()).isNull();
        assertThat(record.inboundNo()).isNull();
        assertThat(record.purchaseOrderNo()).isNull();
        assertThat(record.quantity()).isNull();
        assertThat(record.weighWeightTon()).isNull();
        assertThat(record.brand()).isNull();
        assertThat(record.material()).isNull();
        assertThat(record.spec()).isNull();
        assertThat(record.materialCode()).isNull();
        assertThat(record.category()).isNull();
        assertThat(record.unit()).isNull();
        assertThat(record.warehouseName()).isNull();
        assertThat(record.batchNo()).isNull();
    }

    @Test
    void shouldHandleNullValuesInSourcePurchaseOrderItemRecord() {
        PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord record = new PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord(
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertThat(record.id()).isNull();
        assertThat(record.quantity()).isNull();
        assertThat(record.weightTon()).isNull();
        assertThat(record.orderNo()).isNull();
        assertThat(record.brand()).isNull();
        assertThat(record.material()).isNull();
        assertThat(record.spec()).isNull();
        assertThat(record.materialCode()).isNull();
        assertThat(record.category()).isNull();
        assertThat(record.unit()).isNull();
        assertThat(record.warehouseName()).isNull();
        assertThat(record.batchNo()).isNull();
    }

    @Test
    void shouldSupportRecordEquality() {
        PurchaseItemQueryAppService.SourceInboundItemRecord record1 = new PurchaseItemQueryAppService.SourceInboundItemRecord(
                1L, "INB-001", "PO-001", 100, new BigDecimal("5.5"),
                "BrandA", "MaterialB", "SpecC", "MC-001", "CategoryD", "UnitE", "WarehouseF", "BatchG"
        );
        PurchaseItemQueryAppService.SourceInboundItemRecord record2 = new PurchaseItemQueryAppService.SourceInboundItemRecord(
                1L, "INB-001", "PO-001", 100, new BigDecimal("5.5"),
                "BrandA", "MaterialB", "SpecC", "MC-001", "CategoryD", "UnitE", "WarehouseF", "BatchG"
        );

        assertThat(record1).isEqualTo(record2);
        assertThat(record1.hashCode()).isEqualTo(record2.hashCode());
    }

    @Test
    void shouldSupportRecordToString() {
        PurchaseItemQueryAppService.PieceWeightSummary summary = new PurchaseItemQueryAppService.PieceWeightSummary(
                1L, new BigDecimal("10.5")
        );

        String toString = summary.toString();

        assertThat(toString).contains("purchaseOrderItemId=1");
        assertThat(toString).contains("remainingWeight=10.5");
    }

    @Test
    void shouldMockFindSourceInboundItemsByIds() {
        PurchaseItemQueryAppService service = mock(PurchaseItemQueryAppService.class);
        Collection<Long> ids = List.of(1L, 2L);
        PurchaseItemQueryAppService.SourceInboundItemRecord record = new PurchaseItemQueryAppService.SourceInboundItemRecord(
                1L, "INB-001", "PO-001", 100, new BigDecimal("5.5"),
                "BrandA", "MaterialB", "SpecC", "MC-001", "CategoryD", "UnitE", "WarehouseF", "BatchG"
        );
        when(service.findSourceInboundItemsByIds(ids)).thenReturn(List.of(record));

        List<PurchaseItemQueryAppService.SourceInboundItemRecord> result = service.findSourceInboundItemsByIds(ids);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        verify(service).findSourceInboundItemsByIds(ids);
    }

    @Test
    void shouldMockFindSourcePurchaseOrderItemsByIds() {
        PurchaseItemQueryAppService service = mock(PurchaseItemQueryAppService.class);
        Collection<Long> ids = List.of(3L, 4L);
        PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord record = new PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord(
                3L, 200, new BigDecimal("10.25"), "PO-002",
                "BrandX", "MaterialY", "SpecZ", "MC-002", "CategoryW", "UnitV", "WarehouseU", "BatchT"
        );
        when(service.findSourcePurchaseOrderItemsByIds(ids)).thenReturn(List.of(record));

        List<PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord> result = service.findSourcePurchaseOrderItemsByIds(ids);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(3L);
        verify(service).findSourcePurchaseOrderItemsByIds(ids);
    }

    @Test
    void shouldReturnEmptyListWhenNoResults() {
        PurchaseItemQueryAppService service = mock(PurchaseItemQueryAppService.class);
        Collection<Long> ids = List.of(999L);
        when(service.findSourceInboundItemsByIds(ids)).thenReturn(List.of());

        List<PurchaseItemQueryAppService.SourceInboundItemRecord> result = service.findSourceInboundItemsByIds(ids);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleMultipleRecords() {
        PurchaseItemQueryAppService service = mock(PurchaseItemQueryAppService.class);
        Collection<Long> ids = List.of(1L, 2L, 3L);
        List<PurchaseItemQueryAppService.SourceInboundItemRecord> records = List.of(
                new PurchaseItemQueryAppService.SourceInboundItemRecord(
                        1L, "INB-001", "PO-001", 100, new BigDecimal("5.5"),
                        "BrandA", "MaterialB", "SpecC", "MC-001", "CategoryD", "UnitE", "WarehouseF", "BatchG"),
                new PurchaseItemQueryAppService.SourceInboundItemRecord(
                        2L, "INB-002", "PO-002", 200, new BigDecimal("10.0"),
                        "BrandX", "MaterialY", "SpecZ", "MC-002", "CategoryW", "UnitV", "WarehouseU", "BatchT"),
                new PurchaseItemQueryAppService.SourceInboundItemRecord(
                        3L, "INB-003", "PO-003", 300, new BigDecimal("15.0"),
                        "BrandP", "MaterialQ", "SpecR", "MC-003", "CategoryS", "UnitT", "WarehouseW", "BatchV")
        );
        when(service.findSourceInboundItemsByIds(ids)).thenReturn(records);

        List<PurchaseItemQueryAppService.SourceInboundItemRecord> result = service.findSourceInboundItemsByIds(ids);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(PurchaseItemQueryAppService.SourceInboundItemRecord::id)
                .containsExactly(1L, 2L, 3L);
    }
}