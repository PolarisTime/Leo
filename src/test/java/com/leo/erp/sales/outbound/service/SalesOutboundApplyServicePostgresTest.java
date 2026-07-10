package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
class SalesOutboundApplyServicePostgresTest {

    private static final long SOURCE_ORDER_ID = 8_830_000_000_000_000_001L;
    private static final long SOURCE_ITEM_1_ID = 8_830_000_000_000_000_101L;
    private static final long SOURCE_ITEM_2_ID = 8_830_000_000_000_000_102L;
    private static final long OUTBOUND_ID = 8_830_000_000_000_000_201L;
    private static final long EXISTING_OUTBOUND_ITEM_ID = 8_830_000_000_000_000_301L;
    private static final long NEW_OUTBOUND_ITEM_ID = 8_830_000_000_000_000_302L;
    private static final String WAREHOUSE_NAME = "AUTO-FLUSH-出库仓";

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesOrderItemRepository salesOrderItemRepository;

    @Autowired
    private SalesOutboundRepository salesOutboundRepository;

    @Test
    void shouldKeepNewItemDetachedWhileSourceServiceQueriesJpa() {
        SalesOrder sourceOrder = sourceOrder();
        salesOrderRepository.saveAndFlush(sourceOrder);
        salesOutboundRepository.saveAndFlush(existingOutbound());
        entityManager.clear();

        SalesOutbound managedOutbound = salesOutboundRepository.findByIdAndDeletedFlagFalse(OUTBOUND_ID)
                .orElseThrow();

        SalesOutboundApplyService service = applyService();
        service.applyItems(
                managedOutbound,
                request(List.of(
                        itemRequest(EXISTING_OUTBOUND_ITEM_ID, SOURCE_ITEM_1_ID, "M1", 2),
                        itemRequest(null, SOURCE_ITEM_2_ID, "M2", 3)
                )),
                new AtomicLong(NEW_OUTBOUND_ITEM_ID)::getAndIncrement
        );

        assertThat(managedOutbound.getItems())
                .extracting(SalesOutboundItem::getId)
                .containsExactly(EXISTING_OUTBOUND_ITEM_ID, NEW_OUTBOUND_ITEM_ID);

        entityManager.flush();
        entityManager.clear();

        SalesOutbound persistedOutbound = salesOutboundRepository.findByIdAndDeletedFlagFalse(OUTBOUND_ID)
                .orElseThrow();
        assertThat(persistedOutbound.getItems())
                .extracting(SalesOutboundItem::getId)
                .containsExactlyInAnyOrder(EXISTING_OUTBOUND_ITEM_ID, NEW_OUTBOUND_ITEM_ID);
        assertThat(persistedOutbound.getItems())
                .filteredOn(item -> NEW_OUTBOUND_ITEM_ID == item.getId())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getSalesOutbound()).isSameAs(persistedOutbound);
                    assertThat(item.getSourceSalesOrderItemId()).isEqualTo(SOURCE_ITEM_2_ID);
                    assertThat(item.getQuantity()).isEqualTo(3);
                });
    }

    private SalesOutboundApplyService applyService() {
        TradeItemMaterialSupport materialSupport = new TradeItemMaterialSupport(() -> List.of(
                new TradeMaterialSnapshot("M1", true),
                new TradeMaterialSnapshot("M2", true)
        ));
        WarehouseSelectionSupport warehouseSelectionSupport =
                new WarehouseSelectionSupport(() -> List.of(WAREHOUSE_NAME));
        SalesOrderItemQueryService queryService = new SalesOrderItemQueryService(
                salesOrderItemRepository,
                mock(ResourceRecordAccessGuard.class)
        );
        SalesOutboundWeightService weightService = mock(SalesOutboundWeightService.class);
        when(weightService.resolveOutboundWeightTon(any(), any(), any(), anyInt()))
                .thenAnswer(invocation -> ((SalesOutboundItemRequest) invocation.getArgument(0)).weightTon());

        return new SalesOutboundApplyService(
                materialSupport,
                warehouseSelectionSupport,
                new SalesOutboundSourceService(queryService, salesOutboundRepository),
                weightService
        );
    }

    private SalesOrder sourceOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(SOURCE_ORDER_ID);
        order.setOrderNo("TEST-SOURCE-SO-AUTO-FLUSH");
        order.setCustomerName("测试客户");
        order.setProjectName("测试项目");
        order.setDeliveryDate(LocalDate.of(2026, 7, 10));
        order.setSalesName("测试销售员");
        order.setSettlementCompanyId(1L);
        order.setSettlementCompanyName("测试结算主体");
        order.setTotalWeight(new BigDecimal("2.00000000"));
        order.setTotalAmount(new BigDecimal("8000.00"));
        order.setStatus(StatusConstants.AUDITED);
        order.setCreatedBy(0L);
        order.setCreatedAt(LocalDateTime.of(2026, 7, 10, 14, 0));
        order.getItems().add(sourceItem(SOURCE_ITEM_1_ID, order, 1, "M1", 10));
        order.getItems().add(sourceItem(SOURCE_ITEM_2_ID, order, 2, "M2", 10));
        return order;
    }

    private SalesOutbound existingOutbound() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(OUTBOUND_ID);
        outbound.setOutboundNo("TEST-SOO-AUTO-FLUSH");
        outbound.setSalesOrderNo("TEST-SOURCE-SO-AUTO-FLUSH");
        outbound.setCustomerName("测试客户");
        outbound.setProjectName("测试项目");
        outbound.setWarehouseName(WAREHOUSE_NAME);
        outbound.setSettlementCompanyId(1L);
        outbound.setSettlementCompanyName("测试结算主体");
        outbound.setOutboundDate(LocalDate.of(2026, 7, 10));
        outbound.setTotalWeight(new BigDecimal("0.20000000"));
        outbound.setTotalAmount(new BigDecimal("800.00"));
        outbound.setStatus(StatusConstants.DRAFT);
        outbound.setCreatedBy(0L);
        outbound.setCreatedAt(LocalDateTime.of(2026, 7, 10, 14, 0));
        outbound.getItems().add(outboundItem(
                EXISTING_OUTBOUND_ITEM_ID,
                outbound,
                1,
                SOURCE_ITEM_1_ID,
                "M1",
                2
        ));
        return outbound;
    }

    private SalesOutboundRequest request(List<SalesOutboundItemRequest> items) {
        return new SalesOutboundRequest(
                "TEST-SOO-AUTO-FLUSH",
                "TEST-SOURCE-SO-AUTO-FLUSH",
                "测试客户",
                "测试项目",
                WAREHOUSE_NAME,
                LocalDate.of(2026, 7, 10),
                StatusConstants.DRAFT,
                null,
                items
        );
    }

    private SalesOutboundItemRequest itemRequest(Long id,
                                                  Long sourceItemId,
                                                  String materialCode,
                                                  int quantity) {
        BigDecimal pieceWeightTon = new BigDecimal("0.10000000");
        BigDecimal weightTon = pieceWeightTon.multiply(BigDecimal.valueOf(quantity));
        BigDecimal unitPrice = new BigDecimal("4000.00");
        return new SalesOutboundItemRequest(
                id,
                "TEST-SOURCE-SO-AUTO-FLUSH",
                sourceItemId,
                materialCode,
                "测试品牌",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                WAREHOUSE_NAME,
                "TEST-BATCH-" + materialCode,
                quantity,
                "件",
                pieceWeightTon,
                1,
                weightTon,
                unitPrice,
                weightTon.multiply(unitPrice)
        );
    }

    private SalesOrderItem sourceItem(Long id,
                                      SalesOrder order,
                                      int lineNo,
                                      String materialCode,
                                      int quantity) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrder(order);
        item.setLineNo(lineNo);
        item.setMaterialCode(materialCode);
        item.setBrand("测试品牌");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setUnit("吨");
        item.setSettlementCompanyId(1L);
        item.setSettlementCompanyName("测试结算主体");
        item.setWarehouseName(WAREHOUSE_NAME);
        item.setBatchNo("TEST-BATCH-" + materialCode);
        item.setQuantity(quantity);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("0.10000000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.00000000"));
        item.setOriginalWeightTon(new BigDecimal("1.00000000"));
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("4000.00"));
        return item;
    }

    private SalesOutboundItem outboundItem(Long id,
                                            SalesOutbound outbound,
                                            int lineNo,
                                            Long sourceItemId,
                                            String materialCode,
                                            int quantity) {
        SalesOutboundItemRequest request = itemRequest(id, sourceItemId, materialCode, quantity);
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(id);
        item.setSalesOutbound(outbound);
        item.setLineNo(lineNo);
        item.setSourceSalesOrderItemId(sourceItemId);
        item.setSettlementCompanyId(1L);
        item.setSettlementCompanyName("测试结算主体");
        item.setMaterialCode(request.materialCode());
        item.setBrand(request.brand());
        item.setCategory(request.category());
        item.setMaterial(request.material());
        item.setSpec(request.spec());
        item.setLength(request.length());
        item.setUnit(request.unit());
        item.setWarehouseName(request.warehouseName());
        item.setBatchNo(request.batchNo());
        item.setQuantity(request.quantity());
        item.setQuantityUnit(request.quantityUnit());
        item.setPieceWeightTon(request.pieceWeightTon());
        item.setPiecesPerBundle(request.piecesPerBundle());
        item.setWeightTon(request.weightTon());
        item.setUnitPrice(request.unitPrice());
        item.setAmount(request.amount());
        return item;
    }
}
