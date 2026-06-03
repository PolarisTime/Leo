package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
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
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
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
        SalesOutboundService service = createService(repository, mock(SalesOutboundMapper.class),
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class));

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
        SalesOutboundService service = createService(repository, mock(SalesOutboundMapper.class),
                mock(TradeItemMaterialSupport.class), mock(WarehouseSelectionSupport.class),
                mock(SalesOrderItemQueryService.class));

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
        SalesOutboundService service = new SalesOutboundService(
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
    void shouldCalculateWeightFromPieceWeightWhenWeightTonNull() {
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq(null), eq(1), eq(true))).thenReturn("AUTO");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        SalesOutbound saved = outboundCaptor.getValue();
        assertThat(saved.getItems().get(0).getWeightTon()).isEqualByComparingTo("7.500");
        assertThat(saved.getTotalWeight()).isEqualByComparingTo("7.500");
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
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
    void shouldResolveOutboundWeightFromPieceWeightRecords() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundService service = new SalesOutboundService(
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
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
        SalesOutboundService service = new SalesOutboundService(
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
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
    void shouldFallbackToPieceWeightCalculationWhenNoSourceSalesOrderItemId() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundService service = new SalesOutboundService(
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq(null), eq(1), eq(true))).thenReturn("AUTO");
        when(warehouseSelectionSupport.normalizeWarehouseName(any(), eq(1), eq(true))).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        SalesOutbound saved = outboundCaptor.getValue();
        assertThat(saved.getItems().get(0).getWeightTon()).isEqualByComparingTo("7.500");
        verify(jdbc, never()).query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any());
    }

    @Test
    void shouldCollectSourceSalesOrderNoFromSourceNoWhenNoSourceItemId() {
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName(any(), eq(1), eq(true))).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        var outboundCaptor = forClass(SalesOutbound.class);
        verify(repository).save(outboundCaptor.capture());
        assertThat(outboundCaptor.getValue().getSalesOrderNo()).isEqualTo("SO-MANUAL");
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
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
        SalesOutboundService service = new SalesOutboundService(
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
    void shouldHandleAssertSourceSalesOrderItemsNotOccupiedWithEmptyItems() {
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq(null), eq(1), eq(true))).thenReturn("AUTO");
        when(warehouseSelectionSupport.normalizeWarehouseName(any(), eq(1), eq(true))).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.create(request);

        verify(repository, never()).findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any());
        verify(repository).save(any());
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper(mapper);

        service.update(7003L, request);

        assertThat(existing.getItems().get(0).getSourceSalesOrderItemId()).isEqualTo(9008L);
    }

    private SalesOutboundService createService(SalesOutboundRepository repository,
                                                SalesOutboundMapper mapper,
                                                TradeItemMaterialSupport materialSupport,
                                                WarehouseSelectionSupport warehouseSelectionSupport,
                                                SalesOrderItemQueryService salesOrderItemQueryService) {
        return new SalesOutboundService(
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
        SalesOrderItem item = new SalesOrderItem();
        item.setId(itemId);
        item.setSalesOrder(salesOrder);
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
}
