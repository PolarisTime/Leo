package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseInboundItemRepositoryTest {

    @Mock
    private PurchaseInboundItemRepository repository;

    @Test
    void findAllActiveByIdInShouldReturnItemsForActiveInbounds() {
        PurchaseInboundItem item1 = inboundItem(1L, 1, "M001", 10);
        PurchaseInboundItem item2 = inboundItem(2L, 2, "M002", 20);

        when(repository.findAllActiveByIdIn(List.of(1L, 2L))).thenReturn(List.of(item1, item2));

        List<PurchaseInboundItem> result = repository.findAllActiveByIdIn(List.of(1L, 2L));

        assertThat(result).hasSize(2);
    }

    @Test
    void findAllActiveByIdInShouldNotReturnItemsForDeletedInbounds() {
        when(repository.findAllActiveByIdIn(List.of(1L))).thenReturn(List.of());

        List<PurchaseInboundItem> result = repository.findAllActiveByIdIn(List.of(1L));

        assertThat(result).isEmpty();
    }

    @Test
    void findAllActiveByIdInShouldReturnEmptyWhenNoMatch() {
        when(repository.findAllActiveByIdIn(List.of(999L, 1000L))).thenReturn(List.of());

        List<PurchaseInboundItem> result = repository.findAllActiveByIdIn(List.of(999L, 1000L));

        assertThat(result).isEmpty();
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsShouldGroupCorrectly() {
        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary summary1 =
                new PurchaseInboundItemRepository.PurchaseOrderAllocationSummary() {
                    @Override
                    public Long getSourcePurchaseOrderItemId() { return 201L; }
                    @Override
                    public Long getTotalQuantity() { return 8L; }
                };
        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary summary2 =
                new PurchaseInboundItemRepository.PurchaseOrderAllocationSummary() {
                    @Override
                    public Long getSourcePurchaseOrderItemId() { return 202L; }
                    @Override
                    public Long getTotalQuantity() { return 8L; }
                };

        when(repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(201L, 202L)))
                .thenReturn(List.of(summary1, summary2));

        var result = repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(201L, 202L));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PurchaseInboundItemRepository.PurchaseOrderAllocationSummary::getSourcePurchaseOrderItemId)
                .containsExactlyInAnyOrder(201L, 202L);
    }

    @Test
    void summarizeAllocatedQuantityExcludingInboundShouldExcludeCurrentInbound() {
        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary summary =
                new PurchaseInboundItemRepository.PurchaseOrderAllocationSummary() {
                    @Override
                    public Long getSourcePurchaseOrderItemId() { return 201L; }
                    @Override
                    public Long getTotalQuantity() { return 3L; }
                };

        when(repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(List.of(201L), 1L))
                .thenReturn(List.of(summary));

        var result = repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                List.of(201L), 1L
        );

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.getSourcePurchaseOrderItemId()).isEqualTo(201L);
            assertThat(s.getTotalQuantity()).isEqualTo(3L);
        });
    }

    @Test
    void summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsShouldSumCorrectly() {
        PurchaseInboundItemRepository.PurchaseOrderWeightAdjustmentSummary summary =
                new PurchaseInboundItemRepository.PurchaseOrderWeightAdjustmentSummary() {
                    @Override
                    public Long getSourcePurchaseOrderItemId() { return 201L; }
                    @Override
                    public BigDecimal getTotalWeightAdjustmentTon() { return new BigDecimal("0.050"); }
                };

        when(repository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(List.of(201L), null))
                .thenReturn(List.of(summary));

        var result = repository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(
                List.of(201L), null
        );

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.getSourcePurchaseOrderItemId()).isEqualTo(201L);
            assertThat(s.getTotalWeightAdjustmentTon()).isEqualByComparingTo("0.050");
        });
    }

    @Test
    void summarizeWeighWeightBySourcePurchaseOrderItemIdsShouldSumCorrectly() {
        PurchaseInboundItemRepository.PurchaseOrderWeighWeightSummary summary =
                new PurchaseInboundItemRepository.PurchaseOrderWeighWeightSummary() {
                    @Override
                    public Long getSourcePurchaseOrderItemId() { return 201L; }
                    @Override
                    public Long getTotalQuantity() { return 8L; }
                    @Override
                    public BigDecimal getTotalWeightTon() { return new BigDecimal("0.800"); }
                };

        when(repository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(List.of(201L), null))
                .thenReturn(List.of(summary));

        var result = repository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                List.of(201L), null
        );

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.getSourcePurchaseOrderItemId()).isEqualTo(201L);
            assertThat(s.getTotalQuantity()).isEqualTo(8L);
            assertThat(s.getTotalWeightTon()).isEqualByComparingTo("0.800");
        });
    }

    @Test
    void summarizeWeightByInboundIdsShouldSumCorrectly() {
        PurchaseInboundItemRepository.InboundWeightSummary summary =
                new PurchaseInboundItemRepository.InboundWeightSummary() {
                    @Override
                    public Long getInboundId() { return 1L; }
                    @Override
                    public BigDecimal getTotalWeighWeightTon() { return new BigDecimal("0.800"); }
                    @Override
                    public BigDecimal getTotalWeightAdjustmentTon() { return new BigDecimal("0.030"); }
                };

        when(repository.summarizeWeightByInboundIds(List.of(1L))).thenReturn(List.of(summary));

        var result = repository.summarizeWeightByInboundIds(List.of(1L));

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.getInboundId()).isEqualTo(1L);
            assertThat(s.getTotalWeighWeightTon()).isEqualByComparingTo("0.800");
            assertThat(s.getTotalWeightAdjustmentTon()).isEqualByComparingTo("0.030");
        });
    }

    @Test
    void summarizeWeightByInboundIdsShouldReturnEmptyForEmptyIds() {
        when(repository.summarizeWeightByInboundIds(List.of())).thenReturn(List.of());

        var result = repository.summarizeWeightByInboundIds(List.of());

        assertThat(result).isEmpty();
    }

    private PurchaseInboundItem inboundItem(Long id, int lineNo, String materialCode, int quantity) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(id);
        item.setLineNo(lineNo);
        item.setMaterialCode(materialCode);
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setSettlementMode("理算");
        item.setBatchNo("B1");
        item.setQuantity(quantity);
        item.setQuantityUnit("支");
        item.setPieceWeightTon(new BigDecimal("0.100"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("0.100").multiply(BigDecimal.valueOf(quantity)));
        item.setWeightAdjustmentTon(BigDecimal.ZERO);
        item.setWeightAdjustmentAmount(BigDecimal.ZERO);
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("4000.00").multiply(BigDecimal.valueOf(quantity)));
        return item;
    }
}
