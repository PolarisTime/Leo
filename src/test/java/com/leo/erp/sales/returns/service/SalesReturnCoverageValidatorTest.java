package com.leo.erp.sales.returns.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.repository.FreightBillSourceOrderRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.repository.SalesReturnItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SalesReturnCoverageValidator 极端情况测试：超退、无来源出库、跨订单与累计上限。
 */
@ExtendWith(MockitoExtension.class)
class SalesReturnCoverageValidatorTest {

    @Mock
    private SalesReturnSourceService sourceService;

    @Mock
    private SalesReturnItemRepository salesReturnItemRepository;

    @Mock
    private FreightBillRepository freightBillRepository;

    @Mock
    private FreightBillSourceOrderRepository freightBillSourceOrderRepository;

    private SalesReturnCoverageValidator validator() {
        return new SalesReturnCoverageValidator(
                sourceService, salesReturnItemRepository, freightBillRepository, freightBillSourceOrderRepository);
    }

    private SalesReturn salesReturn(Long id, SalesReturnItem... items) {
        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setId(id);
        for (SalesReturnItem item : items) {
            item.setSalesReturn(salesReturn);
            salesReturn.getItems().add(item);
        }
        return salesReturn;
    }

    private SalesReturnItem item(Long sourceOutboundItemId, int quantity) {
        SalesReturnItem item = new SalesReturnItem();
        item.setSourceSalesOutboundItemId(sourceOutboundItemId);
        item.setQuantity(quantity);
        return item;
    }

    private SalesOutboundItem sourceOutboundItem(Long outboundItemId, Long orderItemId, int quantity, long orderId) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(orderId);
        outbound.setStatus(StatusConstants.AUDITED);
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(outboundItemId);
        item.setSourceSalesOrderItemId(orderItemId);
        item.setQuantity(quantity);
        item.setSalesOutbound(outbound);
        return item;
    }

    private SalesOrderItem sourceOrderItem(Long orderItemId, long orderId) {
        SalesOrder order = new SalesOrder();
        order.setId(orderId);
        order.setStatus(StatusConstants.AUDITED);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(orderItemId);
        item.setSalesOrder(order);
        return item;
    }

    private void stubSources(SalesOutboundItem... sourceItems) {
        java.util.Map<Long, SalesOutboundItem> outboundMap = new java.util.LinkedHashMap<>();
        java.util.Map<Long, SalesOrderItem> orderItemMap = new java.util.LinkedHashMap<>();
        for (SalesOutboundItem sourceItem : sourceItems) {
            outboundMap.put(sourceItem.getId(), sourceItem);
            orderItemMap.put(sourceItem.getSourceSalesOrderItemId(),
                    sourceOrderItem(sourceItem.getSourceSalesOrderItemId(), sourceItem.getSalesOutbound().getId()));
        }
        when(sourceService.loadSourceOutboundItemMap(any())).thenReturn(outboundMap);
        when(sourceService.loadSourceSalesOrderItemMap(any())).thenReturn(orderItemMap);
    }

    private SalesReturnItemRepository.SourceOutboundReturnSummary summary(Long outboundItemId, long totalQuantity) {
        SalesReturnItemRepository.SourceOutboundReturnSummary summary =
                mock(SalesReturnItemRepository.SourceOutboundReturnSummary.class);
        when(summary.getSourceSalesOutboundItemId()).thenReturn(outboundItemId);
        when(summary.getTotalQuantity()).thenReturn(totalQuantity);
        return summary;
    }

    @Test
    void shouldRejectItemWithoutSourceOutboundItem() {
        SalesReturn salesReturn = salesReturn(1L, item(null, 1));

        assertThatThrownBy(() -> validator().assertCoverage(salesReturn))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库明细不能为空");
    }

    @Test
    void shouldRejectNonPositiveQuantity() {
        SalesReturn salesReturn = salesReturn(1L, item(11L, 0));

        assertThatThrownBy(() -> validator().assertCoverage(salesReturn))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退货数量必须大于0");
    }

    @Test
    void shouldRejectUnknownSourceOutboundItem() {
        SalesReturn salesReturn = salesReturn(1L, item(99L, 1));
        when(sourceService.loadSourceOutboundItemMap(any())).thenReturn(Map.of());

        assertThatThrownBy(() -> validator().assertCoverage(salesReturn))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空、重复或已失效");
    }

    @Test
    void shouldRejectOverReturn() {
        SalesOutboundItem source = sourceOutboundItem(11L, 21L, 10, 31L);
        stubSources(source);
        when(salesReturnItemRepository.summarizeAuditedQuantityBySourceOutboundItemIds(any(), eq(StatusConstants.AUDITED), any()))
                .thenReturn(List.of());

        SalesReturn salesReturn = salesReturn(1L, item(11L, 11));

        assertThatThrownBy(() -> validator().assertCoverage(salesReturn))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过来源出库可退数量");
    }

    @Test
    void shouldRejectCumulativeOverReturn() {
        SalesOutboundItem source = sourceOutboundItem(11L, 21L, 10, 31L);
        stubSources(source);
        SalesReturnItemRepository.SourceOutboundReturnSummary summary = summary(11L, 8L);
        when(salesReturnItemRepository.summarizeAuditedQuantityBySourceOutboundItemIds(any(), eq(StatusConstants.AUDITED), any()))
                .thenReturn(List.of(summary));

        SalesReturn salesReturn = salesReturn(1L, item(11L, 3));

        assertThatThrownBy(() -> validator().assertCoverage(salesReturn))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("剩余可退 2 件");
    }

    @Test
    void shouldRejectCrossOrderReturn() {
        SalesOutboundItem first = sourceOutboundItem(11L, 21L, 10, 31L);
        SalesOutboundItem second = sourceOutboundItem(12L, 22L, 10, 32L);
        stubSources(first, second);

        SalesReturn salesReturn = salesReturn(1L, item(11L, 1), item(12L, 1));

        assertThatThrownBy(() -> validator().assertCoverage(salesReturn))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能来源于一张销售订单");
    }

    @Test
    void shouldAcceptWithinCumulativeCap() {
        SalesOutboundItem source = sourceOutboundItem(11L, 21L, 10, 31L);
        stubSources(source);
        SalesReturnItemRepository.SourceOutboundReturnSummary summary = summary(11L, 7L);
        when(salesReturnItemRepository.summarizeAuditedQuantityBySourceOutboundItemIds(any(), eq(StatusConstants.AUDITED), any()))
                .thenReturn(List.of(summary));

        SalesReturn salesReturn = salesReturn(1L, item(11L, 3));

        assertThatCode(() -> validator().assertCoverage(salesReturn)).doesNotThrowAnyException();
    }
}
