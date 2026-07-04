package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundApplyServiceTest {

    @Test
    void shouldApplyItemsAndSummariesFromSourceSalesOrder() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderItemQueryService queryService = mock(SalesOrderItemQueryService.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOutboundSourceService sourceService = new SalesOutboundSourceService(queryService, repository);
        SalesOutboundApplyService service = new SalesOutboundApplyService(
                materialSupport,
                warehouseSelectionSupport,
                sourceService,
                new SalesOutboundWeightService(mock(JdbcTemplate.class))
        );

        SalesOutbound entity = new SalesOutbound();
        entity.setId(1L);
        entity.setSettlementCompanyId(99L);
        entity.setSettlementCompanyName("旧结算主体");
        entity.setItems(new ArrayList<>());
        SalesOutboundRequest request = request();
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new TradeMaterialSnapshot("M1", false)));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("仓库A", 1, true)).thenReturn("仓库A");
        when(queryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem()));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(anyCollection(), eq(1L))).thenReturn(List.of());

        service.applyItems(entity, request, new java.util.concurrent.atomic.AtomicLong(10)::incrementAndGet);

        assertThat(entity.getSalesOrderNo()).isEqualTo("SO-001");
        assertThat(entity.getWarehouseName()).isEqualTo("仓库A");
        assertThat(entity.getSettlementCompanyId()).isNull();
        assertThat(entity.getSettlementCompanyName()).isNull();
        assertThat(entity.getTotalWeight()).isEqualByComparingTo("2.000");
        assertThat(entity.getTotalAmount()).isEqualByComparingTo("6000.00");
        assertThat(entity.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(11L);
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(201L);
            assertThat(item.getBatchNo()).isEqualTo("B1");
            assertThat(item.getWeightTon()).isEqualByComparingTo("2.000");
        });
        verify(repository).findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(anyCollection(), eq(1L));
    }

    @Test
    void shouldFallbackToRequestSalesOrderNoAndHeaderWarehouseWhenSourceNosEmpty() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOutboundSourceService sourceService = mock(SalesOutboundSourceService.class);
        SalesOutboundWeightService weightService = mock(SalesOutboundWeightService.class);
        SalesOutboundApplyService service = new SalesOutboundApplyService(
                materialSupport,
                warehouseSelectionSupport,
                sourceService,
                weightService
        );

        SalesOutbound entity = entity();
        SalesOutboundRequest request = request(
                "SOO-FALLBACK",
                " SO-MANUAL ",
                "仓库HEADER",
                List.of(itemRequest(201L, " "))
        );
        SalesOrderItem sourceItem = sourceSalesOrderItemWithoutOrder(201L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new TradeMaterialSnapshot("M1", false)));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("仓库HEADER", 1, true)).thenReturn("仓库HEADER");
        when(sourceService.loadSourceSalesOrderItemMap(
                anyListOfSalesOutboundItemRequest(),
                anyListOfSalesOutboundItem()
        )).thenReturn(Map.of(201L, sourceItem));
        when(sourceService.resolveSourceSalesOrderItemId(any(), any(), eq(1))).thenReturn(201L);
        when(sourceService.resolveSourceSalesOrderItem(any(), any(), eq(1))).thenReturn(sourceItem);
        when(weightService.resolveOutboundWeightTon(any(), any(), any(), eq(1))).thenReturn(new BigDecimal("2.000"));

        service.applyItems(entity, request, new java.util.concurrent.atomic.AtomicLong(10)::incrementAndGet);

        assertThat(entity.getSalesOrderNo()).isEqualTo("SO-MANUAL");
        assertThat(entity.getWarehouseName()).isEqualTo("仓库HEADER");
        assertThat(entity.getSettlementCompanyId()).isNull();
        assertThat(entity.getSettlementCompanyName()).isNull();
        assertThat(entity.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getWarehouseName()).isEqualTo("仓库HEADER"));
    }

    @Test
    void shouldFallbackToHeaderWarehouseWhenLineWarehouseIsMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOutboundSourceService sourceService = mock(SalesOutboundSourceService.class);
        SalesOutboundWeightService weightService = mock(SalesOutboundWeightService.class);
        SalesOutboundApplyService service = new SalesOutboundApplyService(
                materialSupport,
                warehouseSelectionSupport,
                sourceService,
                weightService
        );

        SalesOutbound entity = entity();
        SalesOutboundRequest request = request(
                "SOO-HEADER-WAREHOUSE",
                "SO-MANUAL",
                "仓库HEADER",
                List.of(itemRequest(201L, null))
        );
        SalesOrderItem sourceItem = sourceSalesOrderItemWithoutOrder(201L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new TradeMaterialSnapshot("M1", false)));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("仓库HEADER", 1, true)).thenReturn("仓库HEADER");
        when(sourceService.loadSourceSalesOrderItemMap(
                anyListOfSalesOutboundItemRequest(),
                anyListOfSalesOutboundItem()
        )).thenReturn(Map.of(201L, sourceItem));
        when(sourceService.resolveSourceSalesOrderItemId(any(), any(), eq(1))).thenReturn(201L);
        when(sourceService.resolveSourceSalesOrderItem(any(), any(), eq(1))).thenReturn(sourceItem);
        when(weightService.resolveOutboundWeightTon(any(), any(), any(), eq(1))).thenReturn(new BigDecimal("2.000"));

        service.applyItems(entity, request, new java.util.concurrent.atomic.AtomicLong(10)::incrementAndGet);

        assertThat(entity.getWarehouseName()).isEqualTo("仓库HEADER");
        assertThat(entity.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getWarehouseName()).isEqualTo("仓库HEADER"));
    }

    @Test
    void shouldNotCollectSettlementCompanyWhenSourceSalesOrderItemIdMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOutboundSourceService sourceService = mock(SalesOutboundSourceService.class);
        SalesOutboundWeightService weightService = mock(SalesOutboundWeightService.class);
        SalesOutboundApplyService service = new SalesOutboundApplyService(
                materialSupport,
                warehouseSelectionSupport,
                sourceService,
                weightService
        );

        SalesOutbound entity = entity();
        entity.setSettlementCompanyId(99L);
        entity.setSettlementCompanyName("旧结算主体");
        SalesOutboundRequest request = request(
                "SOO-NO-SOURCE-ID",
                null,
                "仓库A",
                List.of(itemRequest(null, "仓库A"))
        );
        SalesOrderItem sourceItem = sourceSalesOrderItemWithSettlement(201L, "SO-IGNORED", 7L, "结算主体A");
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new TradeMaterialSnapshot("M1", false)));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("仓库A", 1, true)).thenReturn("仓库A");
        when(sourceService.loadSourceSalesOrderItemMap(
                anyListOfSalesOutboundItemRequest(),
                anyListOfSalesOutboundItem()
        )).thenReturn(Map.of(201L, sourceItem));
        when(sourceService.resolveSourceSalesOrderItemId(any(), any(), eq(1))).thenReturn(null);
        when(sourceService.resolveSourceSalesOrderItem(any(), any(), eq(1))).thenReturn(sourceItem);
        when(weightService.resolveOutboundWeightTon(any(), any(), any(), eq(1))).thenReturn(new BigDecimal("2.000"));

        service.applyItems(entity, request, new java.util.concurrent.atomic.AtomicLong(10)::incrementAndGet);

        assertThat(entity.getSalesOrderNo()).isNull();
        assertThat(entity.getSettlementCompanyId()).isNull();
        assertThat(entity.getSettlementCompanyName()).isNull();
    }

    @Test
    void shouldSkipMissingSourceItemWhenCollectingSettlementCompany() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOutboundSourceService sourceService = mock(SalesOutboundSourceService.class);
        SalesOutboundWeightService weightService = mock(SalesOutboundWeightService.class);
        SalesOutboundApplyService service = new SalesOutboundApplyService(
                materialSupport,
                warehouseSelectionSupport,
                sourceService,
                weightService
        );

        SalesOutbound entity = entity();
        entity.setSettlementCompanyId(99L);
        entity.setSettlementCompanyName("旧结算主体");
        SalesOutboundRequest request = request(
                "SOO-MISSING-MAP",
                "   ",
                "仓库A",
                List.of(itemRequest(201L, "仓库A"))
        );
        SalesOrderItem sourceItem = sourceSalesOrderItemWithSettlement(201L, "SO-IGNORED", 7L, "结算主体A");
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new TradeMaterialSnapshot("M1", false)));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("仓库A", 1, true)).thenReturn("仓库A");
        when(sourceService.loadSourceSalesOrderItemMap(
                anyListOfSalesOutboundItemRequest(),
                anyListOfSalesOutboundItem()
        )).thenReturn(Map.of());
        when(sourceService.resolveSourceSalesOrderItemId(any(), any(), eq(1))).thenReturn(201L);
        when(sourceService.resolveSourceSalesOrderItem(any(), any(), eq(1))).thenReturn(sourceItem);
        when(weightService.resolveOutboundWeightTon(any(), any(), any(), eq(1))).thenReturn(new BigDecimal("2.000"));

        service.applyItems(entity, request, new java.util.concurrent.atomic.AtomicLong(10)::incrementAndGet);

        assertThat(entity.getSalesOrderNo()).isNull();
        assertThat(entity.getSettlementCompanyId()).isNull();
        assertThat(entity.getSettlementCompanyName()).isNull();
    }

    @Test
    void shouldApplySameSalesSettlementCompanyToHeader() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderItemQueryService queryService = mock(SalesOrderItemQueryService.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOutboundWeightService weightService = mock(SalesOutboundWeightService.class);
        SalesOutboundApplyService service = new SalesOutboundApplyService(
                materialSupport,
                warehouseSelectionSupport,
                new SalesOutboundSourceService(queryService, repository),
                weightService
        );

        SalesOutbound entity = entity();
        SalesOutboundRequest request = request(
                "SOO-SAME-SETTLEMENT",
                null,
                "仓库A",
                List.of(itemRequest(201L, "仓库A"), itemRequest(202L, "仓库A"))
        );
        SalesOrderItem firstItem = sourceSalesOrderItemWithSettlement(201L, "SO-001", 7L, " 结算主体A ");
        SalesOrderItem secondItem = sourceSalesOrderItemWithSettlement(202L, "SO-002", 7L, " 结算主体A ");
        stubTwoLineApplyDependencies(
                materialSupport,
                warehouseSelectionSupport,
                weightService,
                queryService,
                repository,
                entity,
                firstItem,
                secondItem
        );

        service.applyItems(entity, request, new java.util.concurrent.atomic.AtomicLong(10)::incrementAndGet);

        assertThat(entity.getSalesOrderNo()).isEqualTo("SO-001, SO-002");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(7L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("结算主体A");
    }

    @Test
    void shouldRejectDifferentSalesSettlementCompanies() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderItemQueryService queryService = mock(SalesOrderItemQueryService.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOutboundWeightService weightService = mock(SalesOutboundWeightService.class);
        SalesOutboundApplyService service = new SalesOutboundApplyService(
                materialSupport,
                warehouseSelectionSupport,
                new SalesOutboundSourceService(queryService, repository),
                weightService
        );

        SalesOutbound entity = entity();
        SalesOutboundRequest request = request(
                "SOO-DIFFERENT-SETTLEMENT",
                null,
                "仓库A",
                List.of(itemRequest(201L, "仓库A"), itemRequest(202L, "仓库A"))
        );
        SalesOrderItem firstItem = sourceSalesOrderItemWithSettlement(201L, "SO-001", 7L, "结算主体A");
        SalesOrderItem secondItem = sourceSalesOrderItemWithSettlement(202L, "SO-002", 8L, "结算主体B");
        stubTwoLineApplyDependencies(
                materialSupport,
                warehouseSelectionSupport,
                weightService,
                queryService,
                repository,
                entity,
                firstItem,
                secondItem
        );

        assertThatThrownBy(() -> service.applyItems(
                entity,
                request,
                new java.util.concurrent.atomic.AtomicLong(10)::incrementAndGet
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单存在不同客户结算主体");
    }

    @Test
    void shouldApplySettlementCompanyNameWhenSourceHasNameOnly() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderItemQueryService queryService = mock(SalesOrderItemQueryService.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOutboundWeightService weightService = mock(SalesOutboundWeightService.class);
        SalesOutboundApplyService service = new SalesOutboundApplyService(
                materialSupport,
                warehouseSelectionSupport,
                new SalesOutboundSourceService(queryService, repository),
                weightService
        );

        SalesOutbound entity = entity();
        SalesOutboundRequest request = request(
                "SOO-NAME-ONLY-SETTLEMENT",
                null,
                "仓库A",
                List.of(itemRequest(201L, "仓库A"))
        );
        SalesOrderItem item = sourceSalesOrderItemWithSettlement(201L, "SO-001", null, " 结算主体A ");
        when(materialSupport.loadMaterialMap(List.of("M1")))
                .thenReturn(Map.of("M1", new TradeMaterialSnapshot("M1", false)));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName(eq("仓库A"), eq(1), eq(true))).thenReturn("仓库A");
        when(weightService.resolveOutboundWeightTon(any(), any(), any(), eq(1))).thenReturn(new BigDecimal("2.000"));
        when(queryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(item));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(anyCollection(), eq(entity.getId())))
                .thenReturn(List.of());

        service.applyItems(entity, request, new java.util.concurrent.atomic.AtomicLong(10)::incrementAndGet);

        assertThat(entity.getSettlementCompanyId()).isNull();
        assertThat(entity.getSettlementCompanyName()).isEqualTo("结算主体A");
    }

    private SalesOutboundRequest request() {
        return request(
                "SOO-001",
                "FORGED",
                null,
                List.of(new SalesOutboundItemRequest(
                        "SO-001",
                        201L,
                        "M1",
                        "宝钢",
                        "盘螺",
                        "HRB400",
                        "10",
                        null,
                        "吨",
                        "仓库A",
                        "B1",
                        1,
                        "件",
                        new BigDecimal("2.000"),
                        1,
                        new BigDecimal("2.000"),
                        new BigDecimal("3000.00"),
                        null
                ))
        );
    }

    private SalesOutboundRequest request(String outboundNo,
                                         String salesOrderNo,
                                         String warehouseName,
                                         List<SalesOutboundItemRequest> items) {
        return new SalesOutboundRequest(
                outboundNo,
                salesOrderNo,
                "客户A",
                "项目A",
                warehouseName,
                LocalDate.of(2026, 5, 1),
                StatusConstants.DRAFT,
                null,
                items
        );
    }

    private SalesOutbound entity() {
        SalesOutbound entity = new SalesOutbound();
        entity.setId(1L);
        entity.setItems(new ArrayList<>());
        return entity;
    }

    private SalesOutboundItemRequest itemRequest(Long sourceSalesOrderItemId, String warehouseName) {
        return new SalesOutboundItemRequest(
                "SO-001",
                sourceSalesOrderItemId,
                "M1",
                "宝钢",
                "盘螺",
                "HRB400",
                "10",
                null,
                "吨",
                warehouseName,
                "B1",
                1,
                "件",
                new BigDecimal("2.000"),
                1,
                new BigDecimal("2.000"),
                new BigDecimal("3000.00"),
                null
        );
    }

    private SalesOrderItem sourceSalesOrderItem() {
        return sourceSalesOrderItemWithSettlement(201L, "SO-001", null, null);
    }

    private SalesOrderItem sourceSalesOrderItemWithoutOrder(Long itemId) {
        SalesOrderItem item = sourceSalesOrderItemWithSettlement(itemId, "SO-IGNORED", null, null);
        item.setSalesOrder(null);
        return item;
    }

    private SalesOrderItem sourceSalesOrderItemWithSettlement(Long itemId,
                                                             String orderNo,
                                                             Long settlementCompanyId,
                                                             String settlementCompanyName) {
        SalesOrder order = new SalesOrder();
        order.setId(itemId + 1000);
        order.setOrderNo(orderNo);
        order.setStatus(StatusConstants.AUDITED);
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        order.setSettlementCompanyId(settlementCompanyId);
        order.setSettlementCompanyName(settlementCompanyName);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(itemId);
        item.setSalesOrder(order);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("10");
        item.setUnit("吨");
        item.setWarehouseName("仓库A");
        item.setBatchNo("B1");
        item.setQuantity(10);
        item.setWeightTon(new BigDecimal("20.000"));
        return item;
    }

    private void stubTwoLineApplyDependencies(TradeItemMaterialSupport materialSupport,
                                              WarehouseSelectionSupport warehouseSelectionSupport,
                                              SalesOutboundWeightService weightService,
                                              SalesOrderItemQueryService queryService,
                                              SalesOutboundRepository repository,
                                              SalesOutbound entity,
                                              SalesOrderItem firstItem,
                                              SalesOrderItem secondItem) {
        when(materialSupport.loadMaterialMap(List.of("M1", "M1")))
                .thenReturn(Map.of("M1", new TradeMaterialSnapshot("M1", false)));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), anyInt(), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName(eq("仓库A"), anyInt(), eq(true))).thenReturn("仓库A");
        when(weightService.resolveOutboundWeightTon(any(), any(), any(), anyInt())).thenReturn(new BigDecimal("2.000"));
        when(queryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(firstItem, secondItem));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(anyCollection(), eq(entity.getId())))
                .thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<SalesOutboundItemRequest> anyListOfSalesOutboundItemRequest() {
        return any(List.class);
    }

    @SuppressWarnings("unchecked")
    private List<SalesOutboundItem> anyListOfSalesOutboundItem() {
        return any(List.class);
    }
}
