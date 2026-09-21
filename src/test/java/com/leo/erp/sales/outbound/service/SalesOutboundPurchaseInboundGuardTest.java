package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售出库审核前置守卫测试（6.1B）：
 * 只校验来源采购明细累计入库量是否覆盖本行销售数量，不再要求采购订单“完成采购”。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundPurchaseInboundGuardTest {

    @Mock
    private SalesOutboundSourceService sourceService;

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private SalesOutboundPurchaseInboundGuard guard;

    @Test
    void shouldSkipWhenOutboundHasNoItems() {
        assertThatCode(() -> guard.assertPurchaseInboundCompletedBeforeAudit(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.assertPurchaseInboundCompletedBeforeAudit(new SalesOutbound()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldSkipWhenSourceSalesOrderItemHasNoPurchaseReference() {
        SalesOutbound outbound = outboundWithItem(5);
        SalesOrderItem sourceItem = sourceItem(100L, null);
        when(sourceService.loadSourceSalesOrderItemMap(outbound.getItems())).thenReturn(Map.of(100L, sourceItem));

        assertThatCode(() -> guard.assertPurchaseInboundCompletedBeforeAudit(outbound))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldSkipWhenSourceSalesOrderItemMissing() {
        SalesOutbound outbound = outboundWithItem(5);
        when(sourceService.loadSourceSalesOrderItemMap(outbound.getItems())).thenReturn(Map.of());

        assertThatCode(() -> guard.assertPurchaseInboundCompletedBeforeAudit(outbound))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenInboundQuantityMeetsRequirement() {
        SalesOutbound outbound = outboundWithItem(5);
        stubSource(outbound, 100L, 200L);
        stubCoverage(5);

        assertThatCode(() -> guard.assertPurchaseInboundCompletedBeforeAudit(outbound))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenInboundQuantityExceedsRequirement() {
        SalesOutbound outbound = outboundWithItem(5);
        stubSource(outbound, 100L, 200L);
        stubCoverage(8);

        assertThatCode(() -> guard.assertPurchaseInboundCompletedBeforeAudit(outbound))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWhenInboundQuantityBelowRequirement() {
        SalesOutbound outbound = outboundWithItem(5);
        stubSource(outbound, 100L, 200L);
        stubCoverage(4);

        assertThatThrownBy(() -> guard.assertPurchaseInboundCompletedBeforeAudit(outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计入库数量不足");
    }

    @Test
    void shouldRejectWhenNoInboundCoverage() {
        SalesOutbound outbound = outboundWithItem(1);
        stubSource(outbound, 100L, 200L);
        stubCoverage(0);

        assertThatThrownBy(() -> guard.assertPurchaseInboundCompletedBeforeAudit(outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计入库数量不足");
    }

    @Test
    void coverageSqlShouldNotRequirePurchaseCompleted() {
        SalesOutbound outbound = outboundWithItem(3);
        stubSource(outbound, 100L, 200L);
        stubCoverage(3);

        guard.assertPurchaseInboundCompletedBeforeAudit(outbound);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(RowMapper.class), any());
        String sql = sqlCaptor.getValue();
        assertThat(sql).doesNotContain("完成采购");
        assertThat(sql).doesNotContain("source_order.status");
        assertThat(sql).contains("inbound.status IN ('已审核', '完成入库')");
    }

    private void stubSource(SalesOutbound outbound, Long sourceSalesOrderItemId, Long purchaseOrderItemId) {
        when(sourceService.loadSourceSalesOrderItemMap(outbound.getItems()))
                .thenReturn(Map.of(sourceSalesOrderItemId, sourceItem(sourceSalesOrderItemId, purchaseOrderItemId)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubCoverage(int inboundQuantity) {
        lenient().when(jdbc.query(anyString(), any(RowMapper.class), any())).thenAnswer(invocation -> {
            RowMapper rowMapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("inbound_quantity")).thenReturn(inboundQuantity);
            return List.of(rowMapper.mapRow(rs, 0));
        });
    }

    private SalesOutbound outboundWithItem(Integer quantity) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(100L);
        item.setQuantity(quantity);
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(5L);
        outbound.setItems(new java.util.ArrayList<>(List.of(item)));
        return outbound;
    }

    private SalesOrderItem sourceItem(Long id, Long purchaseOrderItemId) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSourcePurchaseOrderItemId(purchaseOrderItemId);
        return item;
    }
}
