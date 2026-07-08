package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderResponseAssemblerTest {

    @Test
    void shouldAppendAvailabilityFieldsToDetailResponse() {
        PurchaseOrder order = order();
        PurchaseInboundItemQueryService inboundQueryService = mock(PurchaseInboundItemQueryService.class);
        ItemAllocationNativeRepository allocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderAvailabilityService availabilityService = new PurchaseOrderAvailabilityService(
                inboundQueryService,
                allocationRepo,
                pieceWeightService
        );
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);

        when(mapper.toResponse(order)).thenReturn(summary(order));
        when(inboundQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(11L)))
                .thenReturn(Map.of(11L, 4L));
        when(allocationRepo.summarizeSalesByPurchaseOrderItems(List.of(11L), null))
                .thenReturn(List.of(allocationProjection(11L, 3L)));
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(11L)))
                .thenReturn(Map.of(11L, new BigDecimal("0.700")));

        PurchaseOrderResponse response = new PurchaseOrderResponseAssembler(mapper, availabilityService)
                .toDetailResponse(order);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.remainingQuantity()).isEqualTo(6);
            assertThat(item.salesRemainingQuantity()).isEqualTo(7);
            assertThat(item.salesRemainingWeightTon()).isEqualByComparingTo("0.700");
        });
    }

    @Test
    void shouldAppendChargeItemsAndPayableTotalsToDetailResponse() {
        PurchaseOrder order = order();
        PurchaseInboundItemQueryService inboundQueryService = mock(PurchaseInboundItemQueryService.class);
        ItemAllocationNativeRepository allocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderAvailabilityService availabilityService = new PurchaseOrderAvailabilityService(
                inboundQueryService,
                allocationRepo,
                pieceWeightService
        );
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        DocumentChargeItemService chargeItemService = mock(DocumentChargeItemService.class);
        List<DocumentChargeItemResponse> chargeItems = List.of(
                new DocumentChargeItemResponse(
                        101L,
                        1,
                        "卸货费",
                        "PAYABLE",
                        "SUPPLIER",
                        7L,
                        "供应商A",
                        new BigDecimal("120.50"),
                        true,
                        null,
                        null,
                        null,
                        "现场"
                ),
                new DocumentChargeItemResponse(
                        102L,
                        2,
                        "内部转运",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        new BigDecimal("30.00"),
                        true,
                        null,
                        null,
                        null,
                        null
                )
        );

        when(mapper.toResponse(order)).thenReturn(summary(order));
        when(inboundQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(11L)))
                .thenReturn(Map.of());
        when(allocationRepo.summarizeSalesByPurchaseOrderItems(List.of(11L), null))
                .thenReturn(List.of());
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(11L)))
                .thenReturn(Map.of());
        when(chargeItemService.listResponses("purchase-order", 1L)).thenReturn(chargeItems);

        PurchaseOrderResponse response = new PurchaseOrderResponseAssembler(mapper, availabilityService, chargeItemService)
                .toDetailResponse(order);

        assertThat(response.totalAmount()).isEqualByComparingTo("4000.00");
        assertThat(response.totalChargeAmount()).isEqualByComparingTo("120.50");
        assertThat(response.payableAmount()).isEqualByComparingTo("4120.50");
        assertThat(response.chargeItems()).extracting(DocumentChargeItemResponse::chargeName)
                .containsExactly("卸货费", "内部转运");
    }

    @Test
    void shouldDelegateSummaryResponseToMapper() {
        PurchaseOrder order = order();
        PurchaseOrderResponse summary = summary(order);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        when(mapper.toResponse(order)).thenReturn(summary);
        PurchaseOrderAvailabilityService availabilityService = new PurchaseOrderAvailabilityService(
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class)
        );

        assertThat(new PurchaseOrderResponseAssembler(mapper, availabilityService).toSummaryResponse(order)).isSameAs(summary);
    }

    @Test
    void shouldLeaveSettlementCompanyEmptyWhenItemHasNoParentOrder() {
        PurchaseOrder order = order();
        order.getItems().get(0).setPurchaseOrder(null);
        PurchaseInboundItemQueryService inboundQueryService = mock(PurchaseInboundItemQueryService.class);
        ItemAllocationNativeRepository allocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderAvailabilityService availabilityService = new PurchaseOrderAvailabilityService(
                inboundQueryService,
                allocationRepo,
                pieceWeightService
        );
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        when(mapper.toResponse(order)).thenReturn(summary(order));
        when(inboundQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(11L)))
                .thenReturn(Map.of());
        when(allocationRepo.summarizeSalesByPurchaseOrderItems(List.of(11L), null))
                .thenReturn(List.of());
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(11L)))
                .thenReturn(Map.of());

        PurchaseOrderResponse response = new PurchaseOrderResponseAssembler(mapper, availabilityService)
                .toDetailResponse(order);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.settlementCompanyId()).isNull();
            assertThat(item.settlementCompanyName()).isNull();
        });
    }

    private PurchaseOrder order() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setOrderDate(LocalDateTime.of(2026, 4, 26, 0, 0));
        order.setBuyerName("李四");
        order.setTotalWeight(new BigDecimal("1.000"));
        order.setTotalAmount(new BigDecimal("4000.00"));
        order.setStatus("草稿");

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(11L);
        item.setLineNo(1);
        item.setPurchaseOrder(order);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");
        item.setQuantity(10);
        item.setQuantityUnit("支");
        item.setPieceWeightTon(new BigDecimal("0.100"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("4000.00"));
        order.getItems().add(item);
        return order;
    }

    private PurchaseOrderResponse summary(PurchaseOrder order) {
        return new PurchaseOrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getSupplierName(),
                order.getOrderDate(),
                order.getBuyerName(),
                order.getTotalWeight(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getRemark(),
                List.of()
        );
    }

    private ItemAllocationNativeRepository.AllocationProjection allocationProjection(Long sourceItemId, Long totalQuantity) {
        return new ItemAllocationNativeRepository.AllocationProjection() {
            @Override
            public Long getSourceItemId() {
                return sourceItemId;
            }

            @Override
            public Long getTotalQuantity() {
                return totalQuantity;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return BigDecimal.ZERO;
            }
        };
    }
}
