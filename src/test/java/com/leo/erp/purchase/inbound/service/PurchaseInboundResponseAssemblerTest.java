package com.leo.erp.purchase.inbound.service;

import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseInboundResponseAssemblerTest {

    @Test
    void shouldSkipWeightSummaryQueryWhenInboundIdsAreEmpty() {
        PurchaseInboundItemRepository itemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundResponseAssembler assembler = new PurchaseInboundResponseAssembler(
                mock(PurchaseInboundMapper.class),
                itemRepository,
                mock(ItemAllocationNativeRepository.class)
        );

        assertThat(assembler.loadInboundWeightSummaryMapByIds(List.of())).isEmpty();
        verifyNoInteractions(itemRepository);
    }

    @Test
    void shouldBuildDetailResponseForInboundWithoutItems() {
        PurchaseInbound inbound = inbound();
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        when(mapper.toResponse(inbound)).thenReturn(summary(inbound));
        ItemAllocationNativeRepository allocationRepository = mock(ItemAllocationNativeRepository.class);
        PurchaseInboundResponseAssembler assembler = new PurchaseInboundResponseAssembler(
                mapper,
                mock(PurchaseInboundItemRepository.class),
                allocationRepository
        );

        PurchaseInboundResponse response = assembler.toDetailResponse(inbound);

        assertThat(response.totalWeighWeightTon()).isEqualByComparingTo("0.00000000");
        assertThat(response.totalWeightAdjustmentTon()).isEqualByComparingTo("0.00000000");
        assertThat(response.items()).isEmpty();
        verifyNoInteractions(allocationRepository);
    }

    @Test
    void shouldUseWeightTonWhenWeighWeightIsMissingAndClampRemainingQuantity() {
        PurchaseInbound inbound = inbound();
        PurchaseInboundItem item = item(11L);
        item.setQuantity(3);
        item.setWeightTon(new BigDecimal("1.200"));
        item.setWeighWeightTon(null);
        item.setWeightAdjustmentTon(null);
        inbound.getItems().add(item);

        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        when(mapper.toResponse(inbound)).thenReturn(summary(inbound));
        ItemAllocationNativeRepository allocationRepository = mock(ItemAllocationNativeRepository.class);
        when(allocationRepository.summarizeSalesByInboundItems(List.of(11L), null))
                .thenReturn(List.of(allocationProjection(11L, 5L)));
        PurchaseInboundResponseAssembler assembler = new PurchaseInboundResponseAssembler(
                mapper,
                mock(PurchaseInboundItemRepository.class),
                allocationRepository
        );

        PurchaseInboundResponse response = assembler.toDetailResponse(inbound);

        assertThat(response.totalWeighWeightTon()).isEqualByComparingTo("1.20000000");
        assertThat(response.totalWeightAdjustmentTon()).isEqualByComparingTo("0.00000000");
        assertThat(response.items()).singleElement().satisfies(i -> {
            assertThat(i.remainingQuantity()).isZero();
            assertThat(i.weighWeightTon()).isNull();
            assertThat(i.weightTon()).isEqualByComparingTo("1.200");
        });
    }

    private PurchaseInbound inbound() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.setInboundNo("IN-001");
        inbound.setPurchaseOrderNo("PO-001");
        inbound.setSupplierName("供应商A");
        inbound.setWarehouseName("一号库");
        inbound.setInboundDate(LocalDate.of(2026, 7, 4));
        inbound.setSettlementMode("理算");
        inbound.setTotalWeight(BigDecimal.ZERO);
        inbound.setTotalAmount(BigDecimal.ZERO);
        inbound.setStatus("草稿");
        return inbound;
    }

    private PurchaseInboundItem item(Long id) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(id);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("9m");
        item.setUnit("吨");
        item.setSourcePurchaseOrderItemId(101L);
        item.setSettlementCompanyId(201L);
        item.setSettlementCompanyName("结算公司");
        item.setWarehouseName("一号库");
        item.setSettlementMode("理算");
        item.setBatchNo("B1");
        item.setQuantity(3);
        item.setQuantityUnit("支");
        item.setPieceWeightTon(new BigDecimal("0.400"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.200"));
        item.setWeightAdjustmentTon(BigDecimal.ZERO);
        item.setWeightAdjustmentAmount(BigDecimal.ZERO);
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("4800.00"));
        return item;
    }

    private PurchaseInboundResponse summary(PurchaseInbound inbound) {
        return new PurchaseInboundResponse(
                inbound.getId(),
                inbound.getInboundNo(),
                inbound.getPurchaseOrderNo(),
                inbound.getSupplierName(),
                inbound.getSettlementCompanyId(),
                inbound.getSettlementCompanyName(),
                inbound.getWarehouseName(),
                inbound.getInboundDate(),
                inbound.getSettlementMode(),
                inbound.getTotalWeight(),
                inbound.getTotalAmount(),
                inbound.getStatus(),
                inbound.getRemark(),
                null,
                null,
                List.of()
        );
    }

    private ItemAllocationNativeRepository.AllocationProjection allocationProjection(Long sourceItemId, Long quantity) {
        return new ItemAllocationNativeRepository.AllocationProjection() {
            @Override
            public Long getSourceItemId() {
                return sourceItemId;
            }

            @Override
            public Long getTotalQuantity() {
                return quantity;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return BigDecimal.ZERO;
            }
        };
    }
}
