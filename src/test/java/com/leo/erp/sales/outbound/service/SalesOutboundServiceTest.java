package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.service.SalesOrderCompletionSyncService;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemMaterialSupportTestDoubles;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.mapper.SalesOutboundMapper;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundServiceTest {

    @Test
    void shouldResolveHeaderWarehouseFromFirstLineWarehouseWhenHeaderWarehouseMissing() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-001", "SO-FORGED", "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        "SO-001", 9001L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 7, "件",
                        new BigDecimal("2.037"), 0, new BigDecimal("14.258"),
                        new BigDecimal("3000.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9001L, "SO-001");

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-001")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        SalesOutbound saved = outboundCaptor.getValue();
        assertThat(saved.getSalesOrderNo()).isEqualTo("SO-001");
        assertThat(saved.getWarehouseName()).isEqualTo("一号码头");
        assertThat(saved.getTotalWeight()).isEqualByComparingTo("14.000");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("42000.00");
        assertThat(saved.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9001L);
            assertThat(item.getWarehouseName()).isEqualTo("一号码头");
            assertThat(item.getWeightTon()).isEqualByComparingTo("14.000");
            assertThat(item.getAmount()).isEqualByComparingTo("42000.00");
        });
    }

    @Test
    void shouldKeepSourcePieceWeightWhenActualOutboundWeightChanges() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-002", "SO-002", "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        "SO-002", 9002L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.249"), 0, new BigDecimal("2.248"),
                        new BigDecimal("3111.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9002L, "SO-002");

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-002")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        SalesOutbound saved = outboundCaptor.getValue();
        assertThat(saved.getTotalWeight()).isEqualByComparingTo("2.000");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("6222.00");
        assertThat(saved.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9002L);
            assertThat(item.getPieceWeightTon()).isEqualByComparingTo("2.24900000");
            assertThat(item.getWeightTon()).isEqualByComparingTo("2.000");
            assertThat(item.getAmount()).isEqualByComparingTo("6222.00");
        });
    }

    @Test
    void shouldAllowZeroUnitPriceWhenOutboundUsesAuditedSalesOrderItem() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-ZERO-PRICE", "SO-ZERO-PRICE", "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        "SO-ZERO-PRICE", 9009L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.500"), 0, new BigDecimal("2.500"),
                        BigDecimal.ZERO, null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9009L, "SO-ZERO-PRICE");

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-ZERO-PRICE")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        SalesOutbound saved = outboundCaptor.getValue();
        assertThat(saved.getSalesOrderNo()).isEqualTo("SO-ZERO-PRICE");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(saved.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getUnitPrice()).isEqualByComparingTo("0.00");
            assertThat(item.getAmount()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    void shouldPreservePersistedSourceSalesOrderItemOnUpdateWhenClientOmitsIt() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutbound existing = buildExistingOutbound(7001L, "SOO-003", "SO-003");
        SalesOutboundItem existingItem = buildExistingOutboundItem(8001L, existing, 9003L);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "IGNORED", "FORGED", "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        8001L, "SO-003", null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.249"), 0, new BigDecimal("2.248"),
                        new BigDecimal("3111.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9003L, "SO-003");

        when(repository.findByIdAndDeletedFlagFalse(7001L)).thenReturn(Optional.of(existing));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.update(7001L, request);

        assertThat(existing.getOutboundNo()).isEqualTo("SOO-003");
        assertThat(existing.getSalesOrderNo()).isEqualTo("SO-003");
        assertThat(existing.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(8001L);
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9003L);
        });
    }

    @Test
    void shouldRejectDuplicateOutboundNoOnCreate() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mock(SalesOutboundMapper.class),
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                salesOrderItemQueryService);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "DUP-001", null, "客户A", "项目A", "一号库",
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号库", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOutboundNoAndDeletedFlagFalse("DUP-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售出库单号已存在");
    }

    @Test
    void shouldRejectWhenSourceSalesOrderItemOccupied() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOutboundService service = createService(repository, mock(SalesOutboundMapper.class),
                materialSupport, warehouseSelectionSupport,
                salesOrderItemQueryService);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-OCC-001", null, "客户A", "项目A", "一号库",
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, 9001L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号库", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        SalesOutbound occupiedOutbound = new SalesOutbound();
        occupiedOutbound.setOutboundNo("SOO-EXIST");
        SalesOutboundItem occupiedItem = new SalesOutboundItem();
        occupiedItem.setSourceSalesOrderItemId(9001L);
        occupiedOutbound.setItems(new ArrayList<>(List.of(occupiedItem)));

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-OCC-001")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq(null), eq(1), eq(true))).thenReturn("AUTO");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9001L, "SO-OCC-001");
        sourceSalesOrderItem.setWarehouseName("一号库");
        sourceSalesOrderItem.setBatchNo("AUTO");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of(occupiedOutbound));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单明细已被销售出库单SOO-EXIST关联");
    }

    @Test
    void shouldDeleteOutbound() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundService service = createService(repository, mock(SalesOutboundMapper.class),
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class));

        SalesOutbound existing = new SalesOutbound();
        existing.setId(1L);
        existing.setOutboundNo("SOO-DEL-001");
        existing.setDeletedFlag(false);
        existing.setCustomerName("C");
        existing.setProjectName("P");
        existing.setWarehouseName("W");
        existing.setOutboundDate(LocalDate.now());
        existing.setStatus("草稿");
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mock(SalesOutboundMapper.class));

        service.delete(1L);

        assertThat(existing.isDeletedFlag()).isTrue();
        verify(repository).save(existing);
    }

    @Test
    void shouldUpdateStatus() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderCompletionSyncService syncService = mock(SalesOrderCompletionSyncService.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        SalesOutboundService service = createService(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                mock(WorkflowTransitionGuard.class), syncService,
                mock(SalesOrderItemQueryService.class),
                mock(PurchaseOrderItemPieceWeightService.class), mock(JdbcTemplate.class)
        );

        SalesOutbound existing = new SalesOutbound();
        existing.setId(1L);
        existing.setOutboundNo("SOO-STS-001");
        existing.setStatus("草稿");
        existing.setCustomerName("C");
        existing.setProjectName("P");
        existing.setWarehouseName("W");
        existing.setOutboundDate(LocalDate.now());
        existing.setSalesOrderNo("SO-001");
        existing.setTotalWeight(BigDecimal.ZERO);
        existing.setTotalAmount(BigDecimal.ZERO);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.updateStatus(1L, StatusConstants.AUDITED);

        assertThat(existing.getStatus()).isEqualTo("已审核");
        verify(syncService).syncBySalesOrderReference("SO-001");
    }

    @Test
    void shouldRejectAuditedSaveWhenSourcePurchaseInboundIsNotCompleted() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundService service = createService(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                materialSupport, warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class), mock(SalesOrderCompletionSyncService.class),
                salesOrderItemQueryService, mock(PurchaseOrderItemPieceWeightService.class), jdbc
        );
        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-PO-PENDING", null, "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), StatusConstants.AUDITED, null,
                List.of(new SalesOutboundItemRequest(
                        null, 9020L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9020L, "SO-PO-PENDING");
        sourceSalesOrderItem.setSourcePurchaseOrderItemId(3020L);

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-PO-PENDING")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq(3020L))).thenReturn(0L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购明细尚未完成采购入库")
                .hasMessageContaining("请先将销售出库保存为预出库");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectStatusAuditWhenSourcePurchaseInboundIsNotCompleted() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundService service = createService(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                mock(WorkflowTransitionGuard.class), mock(SalesOrderCompletionSyncService.class),
                salesOrderItemQueryService,
                mock(PurchaseOrderItemPieceWeightService.class), jdbc
        );
        SalesOutbound existing = new SalesOutbound();
        existing.setId(1L);
        existing.setOutboundNo("SOO-PO-PRE");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setCustomerName("C");
        existing.setProjectName("P");
        existing.setWarehouseName("W");
        existing.setOutboundDate(LocalDate.now());
        existing.setSalesOrderNo("SO-PO-PRE");
        existing.setTotalWeight(BigDecimal.ZERO);
        existing.setTotalAmount(BigDecimal.ZERO);
        existing.setItems(new ArrayList<>());
        buildExistingOutboundItem(8020L, existing, 9021L);
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9021L, "SO-PO-PRE");
        sourceSalesOrderItem.setSourcePurchaseOrderItemId(3021L);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq(3021L))).thenReturn(0L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        assertThatThrownBy(() -> service.updateStatus(1L, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购明细尚未完成采购入库")
                .hasMessageContaining("采购入库过磅同步重量后再审核");
        assertThat(existing.getStatus()).isEqualTo(StatusConstants.DRAFT);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldLoadDetailWithSourceNo() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper,
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                salesOrderItemQueryService);

        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("SOO-DET-001");
        outbound.setSalesOrderNo("SO-001");
        outbound.setCustomerName("客户A");
        outbound.setProjectName("项目A");
        outbound.setWarehouseName("一号库");
        outbound.setOutboundDate(LocalDate.of(2026, 5, 1));
        outbound.setTotalWeight(new BigDecimal("10.000"));
        outbound.setTotalAmount(new BigDecimal("30000.00"));
        outbound.setStatus("已审核");

        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(101L);
        item.setSalesOutbound(outbound);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(201L);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("10");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setQuantity(5);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("10.000"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("30000.00"));
        outbound.setItems(new ArrayList<>(List.of(item)));

        SalesOrder sourceOrder = new SalesOrder();
        sourceOrder.setId(501L);
        sourceOrder.setOrderNo("SO-001");
        SalesOrderItem sourceItem = new SalesOrderItem();
        sourceItem.setId(201L);
        sourceItem.setSalesOrder(sourceOrder);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(outbound));
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        stubMapper(mapper);

        SalesOutboundResponse response = service.detail(1L);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceNo()).isEqualTo("SO-001");
    }

    @Test
    void shouldRejectMissingSourceSalesOrderItemWhenWeightTonNull() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-CALC-001", null, "客户A", "项目A", "一号库",
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号库", null, 3, "件",
                        new BigDecimal("2.500"), 1, null,
                        new BigDecimal("3000.00"), null
                ))
        );

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-CALC-001")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不能为空");
    }

    @Test
    void shouldSearchByKeyword() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        SalesOutboundService service = createService(repository, mapper,
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class));

        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        stubMapper(mapper);

        service.search("test", 10);

        verify(repository).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void shouldRejectDuplicateOutboundNoOnUpdateWhenOutboundNoChanges() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundService service = createService(repository, mock(SalesOutboundMapper.class),
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class));

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-NEW", null, "C", "P", "W",
                LocalDate.now(), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "W", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-NEW")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售出库单号已存在");
    }

    @Test
    void shouldAllowUpdateWhenOutboundNoUnchanged() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutbound existing = buildExistingOutbound(7002L, "SOO-SAME", "SO-SAME");
        existing.getItems().clear();

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-SAME", null, "客户A", "项目A", "一号码头",
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, 9004L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.249"), 0, new BigDecimal("2.248"),
                        new BigDecimal("3111.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9004L, "SO-SAME");

        when(repository.findByIdAndDeletedFlagFalse(7002L)).thenReturn(Optional.of(existing));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.update(7002L, request);

        assertThat(existing.getOutboundNo()).isEqualTo("SOO-SAME");
        verify(repository).save(any());
    }

    @Test
    void shouldAllowChangedOutboundNoWhenNoDuplicateExists() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundService service = createService(repository, mock(SalesOutboundMapper.class),
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class));
        SalesOutbound existing = new SalesOutbound();
        existing.setOutboundNo("SOO-OLD");
        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-NEW", null, "C", "P", "W",
                LocalDate.now(), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, 9001L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "W", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );
        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-NEW")).thenReturn(false);

        service.validateUpdate(existing, request);

        verify(repository).existsByOutboundNoAndDeletedFlagFalse("SOO-NEW");
    }

    @Test
    void shouldTreatBlankHeaderSourceNoWithSourceItemAsImportedWhenUpdating() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutbound existing = buildExistingOutbound(7003L, "SOO-BLANK-SOURCE", "   ");
        existing.getItems().clear();
        SalesOutboundItem existingItem = buildExistingOutboundItem(8003L, existing, 9008L);
        existing.setItems(new ArrayList<>(List.of(existingItem)));
        SalesOutboundRequest request = new SalesOutboundRequest(
                "FORGED", "FORGED-SO", "篡改客户", "篡改项目", "篡改仓库",
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        8003L, null, null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "篡改仓库", "B1", 2, "件",
                        new BigDecimal("2.249"), 0, new BigDecimal("2.248"),
                        new BigDecimal("3111.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9008L, "SO-BLANK-SOURCE");

        when(repository.findByIdAndDeletedFlagFalse(7003L)).thenReturn(Optional.of(existing));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.update(7003L, request);

        assertThat(existing.getOutboundNo()).isEqualTo("SOO-BLANK-SOURCE");
        assertThat(existing.getSalesOrderNo()).isEqualTo("SO-BLANK-SOURCE");
        assertThat(existing.getCustomerName()).isEqualTo("客户A");
        assertThat(existing.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9008L));
    }

    @Test
    void shouldResolveOutboundWeightFromPieceWeightRecords() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundService service = createService(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                materialSupport, warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class), mock(SalesOrderCompletionSyncService.class),
                salesOrderItemQueryService, mock(PurchaseOrderItemPieceWeightService.class), jdbc
        );

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-PIECE-001", null, "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, 9005L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 2, "件",
                        new BigDecimal("2.000"), 0, null,
                        new BigDecimal("3000.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9005L, "SO-PIECE");

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-PIECE-001")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName(any(), eq(1), eq(true))).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(9005L))).thenReturn(
                List.of(new BigDecimal("5.100"), new BigDecimal("4.900"), new BigDecimal("4.800")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        SalesOutbound saved = outboundCaptor.getValue();
        assertThat(saved.getItems().get(0).getWeightTon()).isEqualByComparingTo("10.000");
    }

    @Test
    void shouldFallbackWhenPieceWeightRecordsInsufficient() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundService service = createService(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                materialSupport, warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class), mock(SalesOrderCompletionSyncService.class),
                salesOrderItemQueryService, mock(PurchaseOrderItemPieceWeightService.class), jdbc
        );

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-FALLBACK-001", null, "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, 9006L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 3, "件",
                        new BigDecimal("2.000"), 0, new BigDecimal("6.000"),
                        new BigDecimal("3000.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9006L, "SO-FALLBACK");

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-FALLBACK-001")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName(any(), eq(1), eq(true))).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(9006L))).thenReturn(
                List.of(new BigDecimal("5.000")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        SalesOutbound saved = outboundCaptor.getValue();
        assertThat(saved.getItems().get(0).getWeightTon()).isEqualByComparingTo("6.000");
    }

    @Test
    void shouldRejectMissingSourceSalesOrderItemId() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundService service = createService(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                materialSupport, warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class), mock(SalesOrderCompletionSyncService.class),
                salesOrderItemQueryService, mock(PurchaseOrderItemPieceWeightService.class), jdbc
        );

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-NO-SRC-001", null, "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", null, 3, "件",
                        new BigDecimal("2.500"), 1, null,
                        new BigDecimal("3000.00"), null
                ))
        );

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-NO-SRC-001")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不能为空");
        verify(jdbc, never()).query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any());
    }

    @Test
    void shouldRejectSourceNoWithoutSourceSalesOrderItemId() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-SRC-NO-001", null, "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, "SO-MANUAL", null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-SRC-NO-001")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不能为空");
    }

    @Test
    void shouldHandleToDetailResponseWhenSourceSalesOrderItemNotFound() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper,
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                salesOrderItemQueryService);

        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("SOO-DET-NULL");
        outbound.setSalesOrderNo("SO-001");
        outbound.setCustomerName("客户A");
        outbound.setProjectName("项目A");
        outbound.setWarehouseName("一号库");
        outbound.setOutboundDate(LocalDate.of(2026, 5, 1));
        outbound.setTotalWeight(new BigDecimal("2.000"));
        outbound.setTotalAmount(new BigDecimal("6000.00"));
        outbound.setStatus("已审核");

        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(101L);
        item.setSalesOutbound(outbound);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(201L);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("10");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("2.000"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("6000.00"));
        outbound.setItems(new ArrayList<>(List.of(item)));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(outbound));
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of());
        stubMapper(mapper);

        SalesOutboundResponse response = service.detail(1L);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceNo()).isNull();
    }

    @Test
    void shouldHandleToDetailResponseWhenSourceSalesOrderItemHasNoOrder() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper,
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                salesOrderItemQueryService);

        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("SOO-DET-NOORD");
        outbound.setSalesOrderNo("SO-001");
        outbound.setCustomerName("客户A");
        outbound.setProjectName("项目A");
        outbound.setWarehouseName("一号库");
        outbound.setOutboundDate(LocalDate.of(2026, 5, 1));
        outbound.setTotalWeight(new BigDecimal("2.000"));
        outbound.setTotalAmount(new BigDecimal("6000.00"));
        outbound.setStatus("已审核");

        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(101L);
        item.setSalesOutbound(outbound);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(201L);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("10");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("2.000"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("6000.00"));
        outbound.setItems(new ArrayList<>(List.of(item)));

        SalesOrderItem sourceItem = new SalesOrderItem();
        sourceItem.setId(201L);
        sourceItem.setSalesOrder(null);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(outbound));
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        stubMapper(mapper);

        SalesOutboundResponse response = service.detail(1L);

        assertThat(response).isNotNull();
        assertThat(response.items().get(0).sourceNo()).isNull();
    }

    @Test
    void shouldCollectSourceSalesOrderNosWhenSourceItemExists() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOrderItem sourceItem = buildSalesOrderItem(9007L, "SO-COLLECT");
        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-COLLECT-001", null, "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, 9007L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-COLLECT-001")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName(any(), eq(1), eq(true))).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        assertThat(outboundCaptor.getValue().getSalesOrderNo()).isEqualTo("SO-COLLECT");
    }

    @Test
    void shouldRejectWhenSourceSalesOrderItemNullInCollectSourceSalesOrderNos() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-NULL-SRC-001", null, "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, 9999L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-NULL-SRC-001")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName(any(), eq(1), eq(true))).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不存在");
    }

    @Test
    void shouldHandleToDetailResponseWithNoSourceSalesOrderItemId() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper,
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                salesOrderItemQueryService);

        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("SOO-NO-SRC-ID");
        outbound.setSalesOrderNo("SO-001");
        outbound.setCustomerName("客户A");
        outbound.setProjectName("项目A");
        outbound.setWarehouseName("一号库");
        outbound.setOutboundDate(LocalDate.of(2026, 5, 1));
        outbound.setTotalWeight(new BigDecimal("2.000"));
        outbound.setTotalAmount(new BigDecimal("6000.00"));
        outbound.setStatus("已审核");

        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(101L);
        item.setSalesOutbound(outbound);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(null);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("10");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("2.000"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("6000.00"));
        outbound.setItems(new ArrayList<>(List.of(item)));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(outbound));
        stubMapper(mapper);

        SalesOutboundResponse response = service.detail(1L);

        assertThat(response).isNotNull();
        assertThat(response.items().get(0).sourceNo()).isNull();
    }

    @Test
    void shouldUpdateStatusAndTriggerCompletionSync() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderCompletionSyncService syncService = mock(SalesOrderCompletionSyncService.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        SalesOutboundService service = createService(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                mock(WorkflowTransitionGuard.class), syncService,
                mock(SalesOrderItemQueryService.class),
                mock(PurchaseOrderItemPieceWeightService.class), mock(JdbcTemplate.class)
        );

        SalesOutbound existing = new SalesOutbound();
        existing.setId(1L);
        existing.setOutboundNo("SOO-SYNC-001");
        existing.setStatus("草稿");
        existing.setCustomerName("C");
        existing.setProjectName("P");
        existing.setWarehouseName("W");
        existing.setOutboundDate(LocalDate.now());
        existing.setSalesOrderNo("SO-SYNC");
        existing.setTotalWeight(BigDecimal.ZERO);
        existing.setTotalAmount(BigDecimal.ZERO);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.updateStatus(1L, StatusConstants.AUDITED);

        assertThat(existing.getStatus()).isEqualTo("已审核");
        verify(syncService).syncBySalesOrderReference("SO-SYNC");
    }

    @Test
    void shouldRejectMissingSourceBeforeOccupationCheck() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-EMPTY-001", null, "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        null, null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-EMPTY-001")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不能为空");
        verify(repository, never()).findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any());
    }

    @Test
    void shouldResolveSourceSalesOrderItemIdFromExistingItem() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutbound existing = buildExistingOutbound(7003L, "SOO-RESOLVE", "SO-RESOLVE");
        SalesOutboundItem existingItem = buildExistingOutboundItem(8002L, existing, 9008L);

        SalesOutboundRequest request = new SalesOutboundRequest(
                "IGNORED", "FORGED", "客户A", "项目A", null,
                LocalDate.of(2026, 4, 30), "草稿", null,
                List.of(new SalesOutboundItemRequest(
                        8002L, "SO-RESOLVE", null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 1, "件",
                        new BigDecimal("2.249"), 0, new BigDecimal("2.248"),
                        new BigDecimal("3111.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9008L, "SO-RESOLVE");

        when(repository.findByIdAndDeletedFlagFalse(7003L)).thenReturn(Optional.of(existing));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.update(7003L, request);

        assertThat(existing.getItems().get(0).getSourceSalesOrderItemId()).isEqualTo(9008L);
    }

    @Test
    void shouldOnlyAllowOutboundDateRemarkAndQuantityWhenUpdatingImportedOutbound() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);

        SalesOutbound existing = buildExistingOutbound(7004L, "SOO-LOCK", "SO-LOCK");
        SalesOutboundItem existingItem = buildExistingOutboundItem(8004L, existing, 9010L);
        SalesOutboundRequest request = new SalesOutboundRequest(
                "FORGED-NO", "FORGED-SO", "伪造客户", "伪造项目", "二号码头",
                LocalDate.of(2026, 5, 2), "草稿", "仅允许备注",
                List.of(new SalesOutboundItemRequest(
                        8004L, "FORGED-SO", 9010L, "M-FORGED", "伪造品牌", "伪造品类", "伪造材质", "99",
                        null, "吨", "二号码头", "B-FORGED", 3, "箱",
                        new BigDecimal("9.999"), 9, new BigDecimal("99.999"),
                        new BigDecimal("1.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9010L, "SO-LOCK");

        when(repository.findByIdAndDeletedFlagFalse(7004L)).thenReturn(Optional.of(existing));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.update(7004L, request);

        assertThat(existing.getOutboundNo()).isEqualTo("SOO-LOCK");
        assertThat(existing.getSalesOrderNo()).isEqualTo("SO-LOCK");
        assertThat(existing.getCustomerName()).isEqualTo("客户A");
        assertThat(existing.getProjectName()).isEqualTo("项目A");
        assertThat(existing.getWarehouseName()).isEqualTo("一号码头");
        assertThat(existing.getOutboundDate()).isEqualTo(LocalDate.of(2026, 5, 2));
        assertThat(existing.getRemark()).isEqualTo("仅允许备注");
        assertThat(existing.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(existingItem.getId());
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9010L);
            assertThat(item.getMaterialCode()).isEqualTo("M1");
            assertThat(item.getBrand()).isEqualTo("宝钢");
            assertThat(item.getCategory()).isEqualTo("盘螺");
            assertThat(item.getMaterial()).isEqualTo("HRB400");
            assertThat(item.getSpec()).isEqualTo("10");
            assertThat(item.getWarehouseName()).isEqualTo("一号码头");
            assertThat(item.getBatchNo()).isEqualTo("B1");
            assertThat(item.getQuantity()).isEqualTo(3);
            assertThat(item.getQuantityUnit()).isEqualTo("件");
            assertThat(item.getPieceWeightTon()).isEqualByComparingTo("2.24800000");
            assertThat(item.getPiecesPerBundle()).isZero();
            assertThat(item.getWeightTon()).isEqualByComparingTo("6.000");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("3111.00");
            assertThat(item.getAmount()).isEqualByComparingTo("18666.00");
        });
    }

    @Test
    void shouldPageSalesOutboundsWithFilter() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        SalesOutboundService service = createService(
                repository,
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class)
        );
        SalesOutbound outbound = buildExistingOutbound(7005L, "SOO-PAGE", "SO-PAGE");
        outbound.setTotalWeight(new BigDecimal("2.000"));
        outbound.setTotalAmount(new BigDecimal("6000.00"));
        when(repository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(outbound)));
        stubMapper(mapper);

        var page = service.page(
                com.leo.erp.common.api.PageQuery.of(0, 20, "outboundDate", "desc"),
                com.leo.erp.common.api.PageFilter.of(
                        "SOO",
                        "客户A",
                        "项目A",
                        9L,
                        StatusConstants.DRAFT,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 30)
                )
        );

        assertThat(page.getContent()).singleElement().satisfies(response -> {
            assertThat(response.outboundNo()).isEqualTo("SOO-PAGE");
            assertThat(response.salesOrderNo()).isEqualTo("SO-PAGE");
        });
        verify(repository).findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)
        );
    }

    @Test
    void shouldAllowRegularUpdateWhenOutboundWasNotImported() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);
        SalesOutbound existing = buildExistingOutbound(7006L, "SOO-PLAIN", null);
        SalesOutboundRequest request = new SalesOutboundRequest(
                "FORGED-OUTBOUND-NO", "FORGED-SO", "客户B", "项目B", "二号码头",
                LocalDate.of(2026, 5, 3), StatusConstants.DRAFT, "普通更新备注",
                List.of(new SalesOutboundItemRequest(
                        "SO-PLAIN-SRC", 9011L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "二号码头", "B1", 2, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("4.000"),
                        new BigDecimal("3000.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9011L, "SO-PLAIN-SRC");
        sourceSalesOrderItem.getSalesOrder().setCustomerName("客户B");
        sourceSalesOrderItem.getSalesOrder().setProjectName("项目B");
        sourceSalesOrderItem.setWarehouseName("二号码头");

        when(repository.findByIdAndDeletedFlagFalse(7006L)).thenReturn(Optional.of(existing));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("二号码头", 1, true)).thenReturn("二号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.update(7006L, request);

        assertThat(existing.getOutboundNo()).isEqualTo("SOO-PLAIN");
        assertThat(existing.getSalesOrderNo()).isEqualTo("SO-PLAIN-SRC");
        assertThat(existing.getCustomerName()).isEqualTo("客户B");
        assertThat(existing.getProjectName()).isEqualTo("项目B");
        assertThat(existing.getWarehouseName()).isEqualTo("二号码头");
        assertThat(existing.getRemark()).isEqualTo("普通更新备注");
        assertThat(existing.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9011L);
            assertThat(item.getWeightTon()).isEqualByComparingTo("4.000");
            assertThat(item.getAmount()).isEqualByComparingTo("12000.00");
        });
    }

    @Test
    void shouldTreatPersistedSourceItemAsImportedWhenHeaderSalesOrderNoMissing() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);
        SalesOutbound existing = buildExistingOutbound(7007L, "SOO-ITEM-LOCK", null);
        SalesOutboundItem existingItem = buildExistingOutboundItem(8007L, existing, 9012L);
        existingItem.setQuantity(4);
        SalesOutboundRequest request = new SalesOutboundRequest(
                "FORGED-NO", "FORGED-SO", "伪造客户", "伪造项目", "二号码头",
                LocalDate.of(2026, 5, 4), StatusConstants.DRAFT, "按已有明细更新",
                List.of(new SalesOutboundItemRequest(
                        9999L, "FORGED-SO", 9999L, "M-FORGED", "伪造品牌", "伪造品类", "伪造材质", "99",
                        null, "吨", "二号码头", "B-FORGED", 1, "箱",
                        new BigDecimal("9.999"), 9, new BigDecimal("9.999"),
                        new BigDecimal("1.00"), null
                ))
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9012L, "SO-ITEM-LOCK");

        when(repository.findByIdAndDeletedFlagFalse(7007L)).thenReturn(Optional.of(existing));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.update(7007L, request);

        assertThat(existing.getSalesOrderNo()).isEqualTo("SO-ITEM-LOCK");
        assertThat(existing.getCustomerName()).isEqualTo("客户A");
        assertThat(existing.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(8007L);
            assertThat(item.getSourceSalesOrderItemId()).isEqualTo(9012L);
            assertThat(item.getQuantity()).isEqualTo(4);
        });
    }

    @Test
    void shouldRejectDuplicateOutboundNoWhenValidateUpdateReceivesChangedOutboundNo() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundService service = createService(
                repository,
                mock(SalesOutboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class)
        );
        SalesOutbound existing = buildExistingOutbound(7008L, "SOO-OLD", null);
        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-NEW", null, "客户A", "项目A", "一号库",
                LocalDate.of(2026, 5, 5), StatusConstants.DRAFT, null,
                List.of(new SalesOutboundItemRequest(
                        null, 9013L, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号库", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), null
                ))
        );
        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-NEW")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(existing, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售出库单号已存在");
    }

    @Test
    void shouldKeepFirstDuplicateRequestItemWhenRestrictingImportedOutboundUpdate() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundService service = createService(repository, mapper, materialSupport,
                warehouseSelectionSupport, salesOrderItemQueryService);
        SalesOutbound existing = buildExistingOutbound(7009L, "SOO-DUP-ITEM", "SO-DUP-ITEM");
        buildExistingOutboundItem(8009L, existing, 9014L);
        SalesOutboundRequest request = new SalesOutboundRequest(
                "FORGED-NO", "FORGED-SO", "伪造客户", "伪造项目", "二号码头",
                LocalDate.of(2026, 5, 6), StatusConstants.DRAFT, "重复明细",
                List.of(
                        new SalesOutboundItemRequest(
                                8009L, "FORGED-SO", 9014L, "M-FORGED", "伪造品牌", "伪造品类", "伪造材质", "99",
                                null, "吨", "二号码头", "B-FORGED", 2, "箱",
                                new BigDecimal("9.999"), 9, new BigDecimal("99.999"),
                                new BigDecimal("1.00"), null
                        ),
                        new SalesOutboundItemRequest(
                                8009L, "FORGED-SO", 9014L, "M-FORGED", "伪造品牌", "伪造品类", "伪造材质", "99",
                                null, "吨", "二号码头", "B-FORGED", 8, "箱",
                                new BigDecimal("9.999"), 9, new BigDecimal("99.999"),
                                new BigDecimal("1.00"), null
                        )
                )
        );
        SalesOrderItem sourceSalesOrderItem = buildSalesOrderItem(9014L, "SO-DUP-ITEM");

        when(repository.findByIdAndDeletedFlagFalse(7009L)).thenReturn(Optional.of(existing));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.update(7009L, request);

        assertThat(existing.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(2));
    }

    @Test
    void shouldTreatExistingItemWithNullSourceAsRegularOutboundWhenHeaderSourceMissing() {
        SalesOutboundService service = createService(
                mock(SalesOutboundRepository.class),
                mock(SalesOutboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class)
        );
        SalesOutbound existing = buildExistingOutbound(7010L, "SOO-REGULAR", null);
        buildExistingOutboundItem(8010L, existing, null);
        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-REGULAR", null, "客户B", "项目B", "二号码头",
                LocalDate.of(2026, 5, 7), StatusConstants.DRAFT, "普通单据",
                List.of()
        );

        SalesOutboundRequest normalized = service.normalizeUpdateRequest(existing, request);

        assertThat(normalized.customerName()).isEqualTo("客户B");
        assertThat(normalized.projectName()).isEqualTo("项目B");
        assertThat(normalized.warehouseName()).isEqualTo("二号码头");
    }

    @Test
    void shouldExposeVisibleLookupAndNotFoundMessageForDeletedAdminView() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundService service = createService(
                repository,
                mock(SalesOutboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class)
        );
        SalesOutbound deleted = buildExistingOutbound(7011L, "SOO-DELETED", null);
        deleted.setDeletedFlag(true);
        when(repository.findById(7011L)).thenReturn(Optional.of(deleted));

        Optional<SalesOutbound> visible = service.findVisibleEntity(7011L);

        assertThat(visible).containsSame(deleted);
        assertThat(service.notFoundMessage()).isEqualTo("销售出库不存在");
    }

    private SalesOutboundService createService(SalesOutboundRepository repository,
                                                SalesOutboundMapper mapper,
                                                TradeItemMaterialSupport materialSupport,
                                                WarehouseSelectionSupport warehouseSelectionSupport,
                                                SalesOrderItemQueryService salesOrderItemQueryService) {
        return createService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class),
                mock(SalesOrderCompletionSyncService.class),
                salesOrderItemQueryService,
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(JdbcTemplate.class)
        );
    }

    private SalesOutboundService createService(SalesOutboundRepository repository,
                                               SnowflakeIdGenerator idGenerator,
                                               SalesOutboundMapper mapper,
                                               TradeItemMaterialSupport materialSupport,
                                               WarehouseSelectionSupport warehouseSelectionSupport,
                                               WorkflowTransitionGuard workflowTransitionGuard,
                                               SalesOrderCompletionSyncService syncService,
                                               SalesOrderItemQueryService salesOrderItemQueryService,
                                               PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService,
                                               JdbcTemplate jdbc) {
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(materialSupport);
        SalesOutboundSourceService sourceService = new SalesOutboundSourceService(salesOrderItemQueryService, repository);
        return new SalesOutboundService(
                repository,
                idGenerator,
                workflowTransitionGuard,
                new SalesOutboundApplyService(
                        materialSupport,
                        warehouseSelectionSupport,
                        sourceService,
                        new SalesOutboundWeightService(jdbc)
                ),
                new SalesOutboundResponseAssembler(mapper, sourceService),
                new SalesOutboundSaveService(repository, syncService),
                new SalesOutboundPurchaseInboundGuard(sourceService, jdbc)
        );
    }

    private SalesOutbound buildExistingOutbound(Long id, String outboundNo, String salesOrderNo) {
        SalesOutbound existing = new SalesOutbound();
        existing.setId(id);
        existing.setOutboundNo(outboundNo);
        existing.setSalesOrderNo(salesOrderNo);
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");
        existing.setWarehouseName("一号码头");
        existing.setOutboundDate(LocalDate.of(2026, 4, 30));
        existing.setStatus("草稿");
        existing.setItems(new ArrayList<>());
        return existing;
    }

    private SalesOutboundItem buildExistingOutboundItem(Long id, SalesOutbound outbound, Long sourceId) {
        SalesOutboundItem existingItem = new SalesOutboundItem();
        existingItem.setId(id);
        existingItem.setSalesOutbound(outbound);
        existingItem.setLineNo(1);
        existingItem.setSourceSalesOrderItemId(sourceId);
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
        outbound.getItems().add(existingItem);
        return existingItem;
    }

    private SalesOrderItem buildSalesOrderItem(Long itemId, String orderNo) {
        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setId(itemId + 1000);
        salesOrder.setOrderNo(orderNo);
        salesOrder.setStatus(StatusConstants.AUDITED);
        salesOrder.setCustomerName("客户A");
        salesOrder.setProjectName("项目A");
        SalesOrderItem item = new SalesOrderItem();
        item.setId(itemId);
        item.setSalesOrder(salesOrder);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("10");
        item.setUnit("吨");
        item.setWarehouseName("一号码头");
        item.setBatchNo("B1");
        item.setQuantity(100);
        item.setWeightTon(new BigDecimal("200.000"));
        return item;
    }

    private void stubMapper(SalesOutboundMapper mapper) {
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
    }

    private Map<String, TradeMaterialSnapshot> materialMap(String materialCode) {
        return Map.of(materialCode, new TradeMaterialSnapshot(materialCode, Boolean.FALSE));
    }
}
