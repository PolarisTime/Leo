package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundSourceServiceTest {

    @Test
    void shouldResolvePersistedSourceSalesOrderItemIdWhenRequestOmitsIt() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(201L);

        Long sourceId = service.resolveSourceSalesOrderItemId(itemRequest(null, 1), item, 3);

        assertThat(sourceId).isEqualTo(201L);
    }

    @Test
    void shouldRejectNullSourceSalesOrderItemIdWhenResolvingSourceItem() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));

        assertThatThrownBy(() -> service.resolveSourceSalesOrderItem(Map.of(), null, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行来源销售订单明细不能为空");
    }

    @Test
    void shouldRejectSourceSalesOrderItemWithoutOrderWhenResolving() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        SalesOrderItem item = sourceItem(201L, StatusConstants.AUDITED, 10);
        item.setSalesOrder(null);

        assertThatThrownBy(() -> service.resolveSourceSalesOrderItem(Map.of(201L, item), 201L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源销售订单明细不存在");
    }

    @Test
    void shouldRejectMissingSourceIdBeforeValidatingSourceItem() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));

        assertThatThrownBy(() -> service.validateSourceSalesOrderItem(
                itemRequest(null, 1),
                sourceItem(201L, StatusConstants.AUDITED, 10),
                null,
                "客户A",
                "项目A",
                "一号库",
                "B1",
                new HashMap<>(),
                1
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源销售订单明细不能为空");
    }

    @Test
    void shouldRejectWhenRequestedQuantityExceedsSourceQuantity() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        Map<Long, Integer> requestedQuantity = new HashMap<>();
        requestedQuantity.put(201L, 3);

        assertThatThrownBy(() -> service.validateSourceSalesOrderItem(
                itemRequest(201L, 3),
                sourceItem(201L, StatusConstants.AUDITED, 5),
                201L,
                "客户A",
                "项目A",
                "一号库",
                "B1",
                requestedQuantity,
                2
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行来源销售订单明细可出库数量不足，剩余可用 2 件");
    }

    @Test
    void shouldRejectValidationWhenSourceOrderIsMissing() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        SalesOrderItem sourceItem = sourceItem(201L, StatusConstants.AUDITED, 10);
        sourceItem.setSalesOrder(null);

        assertThatThrownBy(() -> service.validateSourceSalesOrderItem(
                itemRequest(201L, 1),
                sourceItem,
                201L,
                "客户A",
                "项目A",
                "一号库",
                "B1",
                new HashMap<>(),
                4
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("第4行来源销售订单未审核，不能作为来源单据");
    }

    @Test
    void shouldTreatNullRequestQuantityAsZeroWhenValidatingSourceItem() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        Map<Long, Integer> requestedQuantity = new HashMap<>();

        service.validateSourceSalesOrderItem(
                itemRequest(201L, null),
                sourceItem(201L, StatusConstants.AUDITED, null),
                201L,
                "客户A",
                "项目A",
                "一号库",
                "B1",
                requestedQuantity,
                1
        );

        assertThat(requestedQuantity).containsEntry(201L, 0);
    }

    @Test
    void shouldAllowNullHeaderCustomerAndProjectWhenSourceTextIsAlsoMissing() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        Map<Long, Integer> requestedQuantity = new HashMap<>();
        SalesOrderItem sourceItem = sourceItem(201L, StatusConstants.AUDITED, 5);
        sourceItem.getSalesOrder().setCustomerName(null);
        sourceItem.getSalesOrder().setProjectName(null);

        service.validateSourceSalesOrderItem(
                itemRequest(201L, 1),
                sourceItem,
                201L,
                null,
                null,
                "一号库",
                "B1",
                requestedQuantity,
                5
        );

        assertThat(requestedQuantity).containsEntry(201L, 1);
    }

    @Test
    void shouldAllowBlankHeaderCustomerAndProjectWhenSourceTextIsMissing() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        Map<Long, Integer> requestedQuantity = new HashMap<>();
        SalesOrderItem sourceItem = sourceItem(201L, StatusConstants.AUDITED, 5);
        sourceItem.getSalesOrder().setCustomerName(null);
        sourceItem.getSalesOrder().setProjectName(null);

        service.validateSourceSalesOrderItem(
                itemRequest(201L, 1),
                sourceItem,
                201L,
                " ",
                " ",
                "一号库",
                "B1",
                requestedQuantity,
                5
        );

        assertThat(requestedQuantity).containsEntry(201L, 1);
    }

    @Test
    void shouldRejectWhenHeaderCustomerOrProjectDiffersFromSourceOrder() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        SalesOrderItem sourceItem = sourceItem(201L, StatusConstants.AUDITED, 5);

        assertThatThrownBy(() -> service.validateSourceSalesOrderItem(
                itemRequest(201L, 1),
                sourceItem,
                201L,
                "客户B",
                "项目A",
                "一号库",
                "B1",
                new HashMap<>(),
                6
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("第6行来源销售订单客户与请求不一致");

        assertThatThrownBy(() -> service.validateSourceSalesOrderItem(
                itemRequest(201L, 1),
                sourceItem,
                201L,
                "客户A",
                "项目B",
                "一号库",
                "B1",
                new HashMap<>(),
                7
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("第7行来源销售订单项目与请求不一致");
    }

    @Test
    void shouldContinueOccupationScanWhenOccupiedOutboundDoesNotContainSourceItem() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundSourceService service = service(repository);
        SalesOutbound occupied = new SalesOutbound();
        occupied.setOutboundNo("SOO-OTHER");
        SalesOutboundItem occupiedItem = new SalesOutboundItem();
        occupiedItem.setSourceSalesOrderItemId(999L);
        occupied.setItems(new ArrayList<>(List.of(occupiedItem)));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), eq(1L)))
                .thenReturn(List.of(occupied));

        assertThatCode(() -> service.assertSourceSalesOrderItemsNotOccupied(List.of(201L), 1L))
                .doesNotThrowAnyException();

        verify(repository).findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), eq(1L));
    }

    @Test
    void shouldCollectManualSourceNoWhenSourceItemIdMissing() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        LinkedHashSet<String> sourceNos = new LinkedHashSet<>();

        service.collectSourceSalesOrderNos(sourceNos, itemRequest(" SO-MANUAL ", null, 1), Map.of(), null);

        assertThat(sourceNos).containsExactly("SO-MANUAL");
    }

    @Test
    void shouldIgnoreBlankManualSourceNoWhenSourceItemIdMissing() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        LinkedHashSet<String> sourceNos = new LinkedHashSet<>();

        service.collectSourceSalesOrderNos(sourceNos, itemRequest("   ", null, 1), Map.of(), null);

        assertThat(sourceNos).isEmpty();
    }

    @Test
    void shouldRejectCollectingSourceNoWhenMappedSourceItemMissing() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));

        assertThatThrownBy(() -> service.collectSourceSalesOrderNos(
                new LinkedHashSet<>(),
                itemRequest(201L, 1),
                Map.of(),
                201L
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不存在");
    }

    @Test
    void shouldRejectCollectingSourceNoWhenMappedSourceItemHasNoOrder() {
        SalesOutboundSourceService service = service(mock(SalesOutboundRepository.class));
        SalesOrderItem item = sourceItem(201L, StatusConstants.AUDITED, 10);
        item.setSalesOrder(null);

        assertThatThrownBy(() -> service.collectSourceSalesOrderNos(
                new LinkedHashSet<>(),
                itemRequest(201L, 1),
                Map.of(201L, item),
                201L
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不存在");
    }

    private SalesOutboundSourceService service(SalesOutboundRepository repository) {
        return new SalesOutboundSourceService(mock(SalesOrderItemQueryService.class), repository);
    }

    private SalesOutboundItemRequest itemRequest(Long sourceSalesOrderItemId, Integer quantity) {
        return itemRequest("SO-001", sourceSalesOrderItemId, quantity);
    }

    private SalesOutboundItemRequest itemRequest(String sourceNo, Long sourceSalesOrderItemId, Integer quantity) {
        return new SalesOutboundItemRequest(
                sourceNo,
                sourceSalesOrderItemId,
                "M1",
                "宝钢",
                "盘螺",
                "HRB400",
                "10",
                null,
                "吨",
                "一号库",
                "B1",
                quantity,
                "件",
                new BigDecimal("2.000"),
                1,
                quantity == null ? null : new BigDecimal("2.000").multiply(BigDecimal.valueOf(quantity)),
                new BigDecimal("3000.00"),
                null
        );
    }

    private SalesOrderItem sourceItem(Long id, String status, Integer quantity) {
        SalesOrder order = new SalesOrder();
        order.setId(101L);
        order.setOrderNo("SO-001");
        order.setStatus(status);
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrder(order);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("10");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");
        item.setQuantity(quantity);
        item.setWeightTon(new BigDecimal("20.000"));
        return item;
    }
}
