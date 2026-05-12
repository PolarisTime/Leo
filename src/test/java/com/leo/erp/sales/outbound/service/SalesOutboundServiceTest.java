package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.sales.order.service.SalesOrderCompletionSyncService;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.mapper.SalesOutboundMapper;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundServiceTest {

    @BeforeEach
    void setUpIdGenerator() {
        ReflectionTestUtils.invokeMethod(new SnowflakeIdGenerator(0L), "registerInstance");
    }

    @Test
    void shouldResolveHeaderWarehouseFromFirstLineWarehouseWhenHeaderWarehouseMissing() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = new SalesOutboundService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class),
                mock(SalesOrderCompletionSyncService.class),
                salesOrderItemQueryService
        );

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-001",
                "SO-FORGED",
                "客户A",
                "项目A",
                null,
                LocalDate.of(2026, 4, 30),
                "草稿",
                null,
                List.of(new SalesOutboundItemRequest(
                        "SO-001", 9001L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 7, "件",
                        new BigDecimal("2.037"), 0, new BigDecimal("14.258"),
                        new BigDecimal("3000.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9001L, "SO-001");

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-001")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(invocation -> {
            SalesOutbound outbound = invocation.getArgument(0);
            return new SalesOutboundResponse(
                    outbound.getId(),
                    outbound.getOutboundNo(),
                    outbound.getSalesOrderNo(),
                    outbound.getCustomerName(),
                    outbound.getProjectName(),
                    outbound.getWarehouseName(),
                    outbound.getOutboundDate(),
                    outbound.getTotalWeight(),
                    outbound.getTotalAmount(),
                    outbound.getStatus(),
                    outbound.getRemark(),
                    List.of()
            );
        });

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        SalesOutbound saved = outboundCaptor.getValue();
        assertThat(saved.getSalesOrderNo()).isEqualTo("SO-001");
        assertThat(saved.getWarehouseName()).isEqualTo("一号码头");
        assertThat(saved.getTotalWeight()).isEqualByComparingTo("14.258");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("42774.00");
        assertThat(saved.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9001L);
            assertThat(item.getWarehouseName()).isEqualTo("一号码头");
            assertThat(item.getWeightTon()).isEqualByComparingTo("14.258");
            assertThat(item.getAmount()).isEqualByComparingTo("42774.00");
        });
    }

    @Test
    void shouldUseRepresentableAveragePieceWeightFromActualOutboundWeight() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = new SalesOutboundService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class),
                mock(SalesOrderCompletionSyncService.class),
                salesOrderItemQueryService
        );

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-002",
                "SO-002",
                "客户A",
                "项目A",
                null,
                LocalDate.of(2026, 4, 30),
                "草稿",
                null,
                List.of(new SalesOutboundItemRequest(
                        "SO-002", 9002L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.249"), 0, new BigDecimal("2.248"),
                        new BigDecimal("3111.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9002L, "SO-002");

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-002")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(invocation -> {
            SalesOutbound outbound = invocation.getArgument(0);
            return new SalesOutboundResponse(
                    outbound.getId(),
                    outbound.getOutboundNo(),
                    outbound.getSalesOrderNo(),
                    outbound.getCustomerName(),
                    outbound.getProjectName(),
                    outbound.getWarehouseName(),
                    outbound.getOutboundDate(),
                    outbound.getTotalWeight(),
                    outbound.getTotalAmount(),
                    outbound.getStatus(),
                    outbound.getRemark(),
                    List.of()
            );
        });

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        SalesOutbound saved = outboundCaptor.getValue();
        assertThat(saved.getTotalWeight()).isEqualByComparingTo("2.248");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("6993.53");
        assertThat(saved.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9002L);
            assertThat(item.getPieceWeightTon()).isEqualByComparingTo("2.248");
            assertThat(item.getWeightTon()).isEqualByComparingTo("2.248");
            assertThat(item.getAmount()).isEqualByComparingTo("6993.53");
        });
    }

    @Test
    void shouldPreservePersistedSourceSalesOrderItemOnUpdateWhenClientOmitsIt() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = new SalesOutboundService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class),
                mock(SalesOrderCompletionSyncService.class),
                salesOrderItemQueryService
        );

        SalesOutbound existing = new SalesOutbound();
        existing.setId(7001L);
        existing.setOutboundNo("SOO-003");
        existing.setSalesOrderNo("SO-003");
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");
        existing.setWarehouseName("一号码头");
        existing.setOutboundDate(LocalDate.of(2026, 4, 30));
        existing.setStatus("草稿");

        SalesOutboundItem existingItem = new SalesOutboundItem();
        existingItem.setId(8001L);
        existingItem.setSalesOutbound(existing);
        existingItem.setLineNo(1);
        existingItem.setSourceSalesOrderItemId(9003L);
        existingItem.setMaterialCode("M1");
        existingItem.setBrand("宝钢");
        existingItem.setCategory("盘螺");
        existingItem.setMaterial("HRB400");
        existingItem.setSpec("10");
        existingItem.setUnit("吨");
        existingItem.setWarehouseName("一号码头");
        existingItem.setBatchNo("B1");
        existingItem.setQuantity(1);
        existingItem.setQuantityUnit("件");
        existingItem.setPieceWeightTon(new BigDecimal("2.248"));
        existingItem.setPiecesPerBundle(0);
        existingItem.setWeightTon(new BigDecimal("2.248"));
        existingItem.setUnitPrice(new BigDecimal("3111.00"));
        existingItem.setAmount(new BigDecimal("6993.53"));
        existing.getItems().add(existingItem);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "IGNORED",
                "FORGED",
                "客户A",
                "项目A",
                null,
                LocalDate.of(2026, 4, 30),
                "草稿",
                null,
                List.of(new SalesOutboundItemRequest(
                        8001L, "SO-003", null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.249"), 0, new BigDecimal("2.248"),
                        new BigDecimal("3111.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9003L, "SO-003");

        when(repository.findByIdAndDeletedFlagFalse(7001L)).thenReturn(java.util.Optional.of(existing));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            SalesOutbound outbound = invocation.getArgument(0);
            return new SalesOutboundResponse(
                    outbound.getId(),
                    outbound.getOutboundNo(),
                    outbound.getSalesOrderNo(),
                    outbound.getCustomerName(),
                    outbound.getProjectName(),
                    outbound.getWarehouseName(),
                    outbound.getOutboundDate(),
                    outbound.getTotalWeight(),
                    outbound.getTotalAmount(),
                    outbound.getStatus(),
                    outbound.getRemark(),
                    List.of()
            );
        }).when(mapper).toResponse(any());

        service.update(7001L, request);

        assertThat(existing.getOutboundNo()).isEqualTo("SOO-003");
        assertThat(existing.getSalesOrderNo()).isEqualTo("SO-003");
        assertThat(existing.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(8001L);
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9003L);
        });
    }

    private SalesOrderItem buildSalesOrderItem(Long itemId, String orderNo) {
        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setId(itemId + 1000);
        salesOrder.setOrderNo(orderNo);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(itemId);
        item.setSalesOrder(salesOrder);
        return item;
    }
}
