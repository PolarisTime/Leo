package com.leo.erp.sales.outbound.service;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.mapper.SalesOutboundMapper;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOutboundResponseAssemblerTest {

    @Test
    void shouldAppendItemResponsesWithResolvedSourceNo() {
        SalesOutbound outbound = outbound();
        outbound.setDeletedFlag(true);
        outbound.setStatus("已审核");
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        when(mapper.toResponse(outbound)).thenReturn(summary(outbound));

        SalesOrderItemQueryService queryService = mock(SalesOrderItemQueryService.class);
        when(queryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceSalesOrderItem()));
        SalesOutboundSourceService sourceService = new SalesOutboundSourceService(
                queryService,
                mock(SalesOutboundRepository.class)
        );

        SalesOutboundResponse response = new SalesOutboundResponseAssembler(mapper, sourceService)
                .toDetailResponse(outbound);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceNo()).isEqualTo("SO-001");
        assertThat(response.items().get(0).sourceSalesOrderItemId()).isEqualTo(201L);
        assertThat(response.status()).isEqualTo("已审核");
        assertThat(response.deletedFlag()).isTrue();
    }

    @Test
    void shouldDelegateSummaryResponseToMapper() {
        SalesOutbound outbound = outbound();
        SalesOutboundResponse summary = summary(outbound);
        SalesOutboundMapper mapper = mock(SalesOutboundMapper.class);
        when(mapper.toResponse(outbound)).thenReturn(summary);

        SalesOutboundSourceService sourceService = new SalesOutboundSourceService(
                mock(SalesOrderItemQueryService.class),
                mock(SalesOutboundRepository.class)
        );

        assertThat(new SalesOutboundResponseAssembler(mapper, sourceService).toSummaryResponse(outbound)).isSameAs(summary);
    }

    private SalesOutbound outbound() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("SOO-001");
        outbound.setSalesOrderNo("SO-001");
        outbound.setCustomerName("客户A");
        outbound.setProjectName("项目A");
        outbound.setWarehouseName("仓库A");
        outbound.setOutboundDate(LocalDate.of(2026, 5, 1));
        outbound.setTotalWeight(new BigDecimal("2.000"));
        outbound.setTotalAmount(new BigDecimal("6000.00"));
        outbound.setStatus("草稿");
        outbound.setItems(new ArrayList<>(List.of(item(outbound))));
        return outbound;
    }

    private SalesOutboundItem item(SalesOutbound outbound) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(11L);
        item.setSalesOutbound(outbound);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(201L);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("10");
        item.setUnit("吨");
        item.setWarehouseName("仓库A");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("2.000"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("6000.00"));
        return item;
    }

    private SalesOrderItem sourceSalesOrderItem() {
        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-001");
        SalesOrderItem item = new SalesOrderItem();
        item.setId(201L);
        item.setSalesOrder(order);
        return item;
    }

    private SalesOutboundResponse summary(SalesOutbound outbound) {
        return new SalesOutboundResponse(
                outbound.getId(),
                outbound.getOutboundNo(),
                outbound.getSalesOrderNo(),
                outbound.getCustomerName(),
                outbound.getProjectName(),
                outbound.getWarehouseName(),
                outbound.getSettlementCompanyId(),
                outbound.getSettlementCompanyName(),
                outbound.getOutboundDate(),
                outbound.getTotalWeight(),
                outbound.getTotalAmount(),
                outbound.getStatus(),
                outbound.isDeletedFlag(),
                outbound.getRemark(),
                List.of()
        );
    }
}
