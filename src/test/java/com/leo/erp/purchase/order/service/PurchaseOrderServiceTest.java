package com.leo.erp.purchase.order.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderServiceTest {

    @Test
    void shouldLoadAllocatedQuantitiesOnlyForCurrentOrderItemsWhenShowingDetail() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderService service = new PurchaseOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                purchaseInboundItemQueryService
        );

        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setOrderDate(LocalDate.of(2026, 4, 26));
        order.setBuyerName("李四");
        order.setTotalWeight(new BigDecimal("2.000"));
        order.setTotalAmount(new BigDecimal("8000.00"));
        order.setStatus("草稿");
        order.setRemark(null);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(7L);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
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
        item.setPurchaseOrder(order);
        order.getItems().add(item);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(mapper.toResponse(order)).thenReturn(new PurchaseOrderResponse(
                1L, "PO-001", "供应商A", LocalDate.of(2026, 4, 26), "李四",
                new BigDecimal("2.000"), new BigDecimal("8000.00"), "草稿", null, List.of()
        ));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(7L)))
                .thenReturn(Map.of(7L, 4L));

        PurchaseOrderResponse response = service.detail(1L);

        assertThat(response.items()).singleElement().satisfies(detailItem ->
                assertThat(detailItem.remainingQuantity()).isEqualTo(6)
        );
        verify(purchaseInboundItemQueryService).summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(7L));
    }
}
