package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
        entity.setItems(new ArrayList<>());
        SalesOutboundRequest request = request();
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new TradeMaterialSnapshot("M1", false)));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("仓库A", 1, true)).thenReturn("仓库A");
        when(queryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem()));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(anyCollection(), eq(1L))).thenReturn(List.of());

        service.applyItems(entity, request, new java.util.concurrent.atomic.AtomicLong(10)::incrementAndGet);

        assertThat(entity.getSalesOrderNo()).isEqualTo("SO-001");
        assertThat(entity.getWarehouseName()).isEqualTo("仓库A");
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

    private SalesOutboundRequest request() {
        return new SalesOutboundRequest(
                "SOO-001",
                "FORGED",
                "客户A",
                "项目A",
                null,
                LocalDate.of(2026, 5, 1),
                StatusConstants.DRAFT,
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

    private SalesOrderItem sourceSalesOrderItem() {
        SalesOrder order = new SalesOrder();
        order.setId(101L);
        order.setOrderNo("SO-001");
        order.setStatus(StatusConstants.AUDITED);
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        SalesOrderItem item = new SalesOrderItem();
        item.setId(201L);
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
}
