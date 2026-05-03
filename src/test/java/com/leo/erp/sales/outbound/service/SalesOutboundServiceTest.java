package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
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
import static org.mockito.ArgumentMatchers.eq;
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
        SalesOutboundService service = new SalesOutboundService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class)
        );

        SalesOutboundRequest request = new SalesOutboundRequest(
                "SOO-001",
                "SO-001",
                "客户A",
                "项目A",
                null,
                LocalDate.of(2026, 4, 30),
                "草稿",
                null,
                List.of(new SalesOutboundItemRequest(
                        null, "M1", "宝钢", "盘螺", "HRB400", "10", null, "吨",
                        "一号码头", "B1", 7, "件",
                        new BigDecimal("2.037"), 0, new BigDecimal("14.258"),
                        new BigDecimal("3000.00"), null
                ))
        );

        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO-001")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号码头", 1, true)).thenReturn("一号码头");
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
        assertThat(saved.getWarehouseName()).isEqualTo("一号码头");
        assertThat(saved.getTotalWeight()).isEqualByComparingTo("14.258");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("42774.00");
        assertThat(saved.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getWarehouseName()).isEqualTo("一号码头");
            assertThat(item.getWeightTon()).isEqualByComparingTo("14.258");
            assertThat(item.getAmount()).isEqualByComparingTo("42774.00");
        });
    }
}
