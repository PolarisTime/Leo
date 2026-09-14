package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundCoverageValidator 累计覆盖测试：支持部分出库、跨出库累计超量拒绝、更新排除自身。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundCoverageValidatorTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOutboundSourceService sourceService;

    @InjectMocks
    private SalesOutboundCoverageValidator validator;

    private SalesOrder order(Long id, String status) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo("SO" + id);
        order.setStatus(status);
        return order;
    }

    private SalesOrderItem sourceItem(Long id, Integer quantity, SalesOrder order) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setQuantity(quantity);
        item.setSalesOrder(order);
        return item;
    }

    private SalesOutboundItem outboundItem(Long sourceId, Integer quantity) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(sourceId);
        item.setQuantity(quantity);
        return item;
    }

    private SalesOutbound outbound(Long id, SalesOutboundItem... items) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(id);
        outbound.setItems(List.of(items));
        return outbound;
    }

    private void stubSourceItems(SalesOrderItem... items) {
        Map<Long, SalesOrderItem> map = new LinkedHashMap<>();
        for (SalesOrderItem item : items) {
            map.put(item.getId(), item);
        }
        when(sourceService.loadSourceSalesOrderItemMap(anyList())).thenReturn(map);
    }

    private void stubAuditedOrder(SalesOrder order) {
        when(salesOrderRepository.findByIdAndDeletedFlagFalse(order.getId())).thenReturn(Optional.of(order));
    }

    @Test
    void shouldRejectEmptyItems() {
        assertThatThrownBy(() -> validator.assertCumulativeCoverage(outbound(5L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("明细不能为空");
    }

    @Test
    void shouldRejectNullSourceItemId() {
        assertThatThrownBy(() -> validator.assertCumulativeCoverage(outbound(5L, outboundItem(null, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不能为空");
    }

    @Test
    void shouldRejectNonPositiveQuantity() {
        assertThatThrownBy(() -> validator.assertCumulativeCoverage(outbound(5L, outboundItem(11L, 0))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须大于等于 1");
    }

    @Test
    void shouldRejectDuplicateSourceItem() {
        assertThatThrownBy(() -> validator.assertCumulativeCoverage(outbound(5L,
                outboundItem(11L, 2), outboundItem(11L, 3))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复导入");
    }

    @Test
    void shouldRejectMissingSourceItem() {
        stubSourceItems();
        assertThatThrownBy(() -> validator.assertCumulativeCoverage(outbound(5L, outboundItem(11L, 2))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源明细不能为空、重复或已失效");
    }

    @Test
    void shouldRejectMultipleOrders() {
        SalesOrder orderA = order(1L, StatusConstants.AUDITED);
        SalesOrder orderB = order(2L, StatusConstants.AUDITED);
        stubSourceItems(
                sourceItem(11L, 10, orderA),
                sourceItem(12L, 10, orderB));

        assertThatThrownBy(() -> validator.assertCumulativeCoverage(outbound(5L,
                outboundItem(11L, 1), outboundItem(12L, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能来源于一张销售订单");
    }

    @Test
    void shouldRejectNonAuditedSourceOrder() {
        SalesOrder order = order(1L, StatusConstants.DRAFT);
        stubSourceItems(sourceItem(11L, 10, order));
        stubAuditedOrder(order);

        assertThatThrownBy(() -> validator.assertCumulativeCoverage(outbound(5L, outboundItem(11L, 2))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已审核订单");
    }

    @Test
    void shouldRejectSourceItemNotBelongingToOrder() {
        SalesOrder order = order(1L, StatusConstants.AUDITED);
        SalesOrderItem orphan = sourceItem(11L, 10, null);
        stubSourceItems(orphan, sourceItem(12L, 10, order));
        stubAuditedOrder(order);

        assertThatThrownBy(() -> validator.assertCumulativeCoverage(outbound(5L,
                outboundItem(11L, 1), outboundItem(12L, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于来源销售订单");
    }

    @Test
    void shouldAllowPartialCoverageOfOrderItems() {
        SalesOrder order = order(1L, StatusConstants.AUDITED);
        stubSourceItems(sourceItem(11L, 10, order), sourceItem(12L, 10, order));
        stubAuditedOrder(order);
        when(sourceService.sumOtherOutboundQuantitiesBySourceSalesOrderItemIds(anyCollection(), any()))
                .thenReturn(Map.of());

        assertThatCode(() -> validator.assertCumulativeCoverage(outbound(5L, outboundItem(11L, 4))))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectCumulativeOverQuantityAndReportRemaining() {
        SalesOrder order = order(1L, StatusConstants.AUDITED);
        stubSourceItems(sourceItem(11L, 10, order));
        stubAuditedOrder(order);
        when(sourceService.sumOtherOutboundQuantitiesBySourceSalesOrderItemIds(anyCollection(), any()))
                .thenReturn(Map.of(11L, 7));

        assertThatThrownBy(() -> validator.assertCumulativeCoverage(outbound(5L, outboundItem(11L, 4))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("剩余可用 3 件");
    }

    @Test
    void shouldAllowExactCumulativeQuantity() {
        SalesOrder order = order(1L, StatusConstants.AUDITED);
        stubSourceItems(sourceItem(11L, 10, order));
        stubAuditedOrder(order);
        when(sourceService.sumOtherOutboundQuantitiesBySourceSalesOrderItemIds(anyCollection(), any()))
                .thenReturn(Map.of(11L, 6));

        assertThatCode(() -> validator.assertCumulativeCoverage(outbound(5L, outboundItem(11L, 4))))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldExcludeCurrentOutboundFromCumulativeCheck() {
        SalesOrder order = order(1L, StatusConstants.AUDITED);
        stubSourceItems(sourceItem(11L, 10, order));
        stubAuditedOrder(order);
        when(sourceService.sumOtherOutboundQuantitiesBySourceSalesOrderItemIds(anyCollection(), any()))
                .thenReturn(Map.of());

        validator.assertCumulativeCoverage(outbound(5L, outboundItem(11L, 4)));

        verify(sourceService).sumOtherOutboundQuantitiesBySourceSalesOrderItemIds(anyCollection(), eq(5L));
    }
}
