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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundSourceService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundSourceServiceTest {

    @Mock
    private SalesOrderItemQueryService salesOrderItemQueryService;

    @Mock
    private SalesOutboundRepository repository;

    @InjectMocks
    private SalesOutboundSourceService service;

    private SalesOrder salesOrder(Long id, String status) {
        SalesOrder o = new SalesOrder();
        o.setId(id);
        o.setOrderNo("SO" + id);
        o.setStatus(status);
        o.setCustomerId(10L);
        o.setCustomerName("客户A");
        o.setProjectId(20L);
        o.setProjectName("项目A");
        return o;
    }

    private SalesOrderItem sourceItem(Long id, Integer quantity, SalesOrder order) {
        SalesOrderItem i = new SalesOrderItem();
        i.setId(id);
        i.setQuantity(quantity);
        i.setSalesOrder(order);
        i.setMaterialId(500L);
        i.setMaterialCode("M001");
        i.setBrand("品牌A");
        i.setCategory("型钢");
        i.setMaterial("螺纹钢");
        i.setSpec("HRB400");
        i.setUnit("吨");
        i.setWarehouseId(1L);
        i.setWarehouseName("库房A");
        i.setBatchNo("B001");
        return i;
    }

    private SalesOutboundItemRequest itemRequest(Long id, Long sourceId) {
        return itemRequest(id, sourceId, 5);
    }

    private SalesOutboundItemRequest itemRequest(Long id, Long sourceId, Integer qty) {
        return new SalesOutboundItemRequest(
                id, "SO001", sourceId, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                1L, "库房A", "B001", qty, "件", new BigDecimal("1.250"), 100, new BigDecimal("12.500"),
                new BigDecimal("4000"), null);
    }

    private SalesOutboundItem outboundItem(Long sourceId) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(sourceId);
        return item;
    }

    // ---------- loadSourceSalesOrderItemMap(requestItems, items) ----------

    @Test
    void loadMapFromRequestsAndItems_shouldMergeAndDedup() {
        SalesOrderItem src11 = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));
        SalesOrderItem src12 = sourceItem(12L, 10, salesOrder(1L, StatusConstants.AUDITED));
        when(salesOrderItemQueryService.findActiveByIdIn(any())).thenReturn(List.of(src11, src12));

        // request 含 11、12；items 含 11（与 request 重复）→ 去重合并后查 {11,12}
        Map<Long, SalesOrderItem> result = service.loadSourceSalesOrderItemMap(
                List.of(itemRequest(null, 11L), itemRequest(null, 12L)), List.of(outboundItem(11L)));

        assertThat(result).containsKeys(11L, 12L);
    }

    @Test
    void loadMapFromRequestsAndItems_shouldReturnEmptyWhenNoIds() {
        Map<Long, SalesOrderItem> result = service.loadSourceSalesOrderItemMap(
                List.of(itemRequest(null, null)), List.of(outboundItem(null)));

        assertThat(result).isEmpty();
        verifyNoInteractions(salesOrderItemQueryService);
    }

    // ---------- loadSourceSalesOrderItemMap(items) ----------

    @Test
    void loadMapFromItems_shouldFilterNullAndDistinct() {
        SalesOrderItem src = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));
        when(salesOrderItemQueryService.findActiveByIdIn(any())).thenReturn(List.of(src));

        Map<Long, SalesOrderItem> result = service.loadSourceSalesOrderItemMap(
                List.of(outboundItem(11L), outboundItem(11L), outboundItem(null)));

        assertThat(result).containsOnlyKeys(11L);
    }

    // ---------- resolveSourceSalesOrderItemId ----------

    @Test
    void resolveSourceId_shouldPreferRequestSourceId() {
        assertThat(service.resolveSourceSalesOrderItemId(itemRequest(null, 11L), outboundItem(99L), 1))
                .isEqualTo(11L);
    }

    @Test
    void resolveSourceId_shouldFallbackToPersistedItemId() {
        SalesOutboundItem item = outboundItem(22L);
        assertThat(service.resolveSourceSalesOrderItemId(itemRequest(null, null), item, 1)).isEqualTo(22L);
    }

    @Test
    void resolveSourceId_shouldThrowWhenBothNull() {
        assertThatThrownBy(() -> service.resolveSourceSalesOrderItemId(itemRequest(null, null), outboundItem(null), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第3行");
    }

    // ---------- resolveSourceSalesOrderItem ----------

    @Test
    void resolveSourceItem_shouldThrowForNullId() {
        assertThatThrownBy(() -> service.resolveSourceSalesOrderItem(Map.of(), null, 3))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolveSourceItem_shouldThrowWhenMissingFromMap() {
        assertThatThrownBy(() -> service.resolveSourceSalesOrderItem(Map.of(), 11L, 3))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolveSourceItem_shouldThrowWhenOrderNull() {
        SalesOrderItem src = sourceItem(11L, 10, null);
        assertThatThrownBy(() -> service.resolveSourceSalesOrderItem(Map.of(11L, src), 11L, 3))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolveSourceItem_shouldReturnWhenPresent() {
        SalesOrderItem src = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));
        assertThat(service.resolveSourceSalesOrderItem(Map.of(11L, src), 11L, 1)).isSameAs(src);
    }

    // ---------- validateSourceSalesOrderItem（完整版） ----------

    private void validate(SalesOutboundItemRequest req, SalesOrderItem src, Long id, String custName,
                          String projName, Map<Long, Integer> qtyMap, int lineNo) {
        service.validateSourceSalesOrderItem(req, src, id, 10L, custName, 20L, projName,
                1L, "库房A", "B001", qtyMap, lineNo);
    }

    @Test
    void validate_shouldThrowForNullSourceId() {
        assertThatThrownBy(() -> validate(itemRequest(null, null), sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED)), null, "客户A", "项目A", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validate_shouldThrowWhenSourceOrderNull() {
        assertThatThrownBy(() -> validate(itemRequest(null, 11L), sourceItem(11L, 10, null), 11L, "客户A", "项目A", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validate_shouldThrowForNonAuditedStatus() {
        SalesOrder order = salesOrder(1L, StatusConstants.DRAFT);
        assertThatThrownBy(() -> validate(itemRequest(null, 11L), sourceItem(11L, 10, order), 11L, "客户A", "项目A", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未审核");
    }

    @Test
    void validate_shouldThrowForCustomerIdMismatch() {
        SalesOrder order = salesOrder(1L, StatusConstants.AUDITED);
        order.setCustomerId(99L);
        assertThatThrownBy(() -> validate(itemRequest(null, 11L), sourceItem(11L, 10, order), 11L, "客户A", "项目A", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户ID");
    }

    @Test
    void validate_shouldThrowForCustomerNameMismatch() {
        assertThatThrownBy(() -> validate(itemRequest(null, 11L), sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED)), 11L, "其他客户", "项目A", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户");
    }

    @Test
    void validate_shouldThrowForProjectNameMismatch() {
        assertThatThrownBy(() -> validate(itemRequest(null, 11L), sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED)), 11L, "客户A", "其他项目", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validate_shouldThrowForMaterialIdMismatch() {
        SalesOutboundItemRequest req = itemRequest(null, 11L, 5);
        SalesOutboundItemRequest bad = new SalesOutboundItemRequest(
                null, "SO001", 11L, 999L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                1L, "库房A", "B001", 5, "件", new BigDecimal("1.250"), 100, new BigDecimal("12.500"),
                new BigDecimal("4000"), null);
        assertThatThrownBy(() -> validate(bad, sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED)), 11L, "客户A", "项目A", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品ID");
    }

    @Test
    void validate_shouldThrowForWarehouseIdMismatch() {
        // warehouseId 通过完整版参数传入（这里 header 传 1L 匹配来源，改为不匹配的 wh 用简化版重载测试）
        assertThatThrownBy(() -> service.validateSourceSalesOrderItem(
                itemRequest(null, 11L, 5), sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED)),
                11L, 10L, "客户A", 20L, "项目A", 99L, "库房A", "B001", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仓库ID");
    }

    @Test
    void validate_shouldThrowWhenQuantityExceedsSource() {
        SalesOutboundItemRequest req = itemRequest(null, 11L, 20); // 需求 20 > 来源 10
        assertThatThrownBy(() -> validate(req, sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED)), 11L, "客户A", "项目A", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可出库数量不足");
    }

    @Test
    void validate_shouldAccumulateQuantityAcrossLines() {
        Map<Long, Integer> qtyMap = new HashMap<>();
        SalesOrderItem src = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));
        validate(itemRequest(null, 11L, 6), src, 11L, "客户A", "项目A", qtyMap, 1);
        validate(itemRequest(null, 11L, 4), src, 11L, "客户A", "项目A", qtyMap, 2);

        assertThat(qtyMap.get(11L)).isEqualTo(10); // 6+4 累计
    }

    @Test
    void validate_shouldRejectAccumulatedExcess() {
        Map<Long, Integer> qtyMap = new HashMap<>();
        SalesOrderItem src = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));
        validate(itemRequest(null, 11L, 6), src, 11L, "客户A", "项目A", qtyMap, 1);

        assertThatThrownBy(() -> validate(itemRequest(null, 11L, 5), src, 11L, "客户A", "项目A", qtyMap, 2))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validate_shouldTreatNullRequestQuantityAsZero() {
        Map<Long, Integer> qtyMap = new HashMap<>();
        SalesOrderItem src = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));
        validate(itemRequest(null, 11L, null), src, 11L, "客户A", "项目A", qtyMap, 1);

        assertThat(qtyMap.get(11L)).isEqualTo(0);
    }

    @Test
    void validate_shouldPassWhenAllMatch() {
        Map<Long, Integer> qtyMap = new HashMap<>();
        SalesOrderItem src = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));
        validate(itemRequest(null, 11L, 5), src, 11L, "客户A", "项目A", qtyMap, 1);

        assertThat(qtyMap).containsEntry(11L, 5);
    }

    // ---------- validateSourceSalesOrderItem（简化版） ----------

    @Test
    void validateShort_shouldDelegateAndSkipHeaderIds() {
        Map<Long, Integer> qtyMap = new HashMap<>();
        SalesOrderItem src = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));

        // headerCustomerId/projectId/warehouseId 走 null（不比对），数量校验生效
        service.validateSourceSalesOrderItem(itemRequest(null, 11L, 5), src, 11L,
                "客户A", "项目A", "库房A", "B001", qtyMap, 1);

        assertThat(qtyMap).containsEntry(11L, 5);
    }

    // ---------- assertSourceSalesOrderItemsNotOccupied ----------

    @Test
    void assertNotOccupied_shouldSkipEmpty() {
        service.assertSourceSalesOrderItemsNotOccupied(List.of(), 5L);
        verifyNoInteractions(repository);
    }

    @Test
    void assertNotOccupied_shouldThrowWhenOccupied() {
        SalesOutbound occupied = new SalesOutbound();
        occupied.setOutboundNo("OB001");
        occupied.setItems(List.of(outboundItem(11L)));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of(occupied));

        assertThatThrownBy(() -> service.assertSourceSalesOrderItemsNotOccupied(List.of(11L), 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OB001");
    }

    @Test
    void assertNotOccupied_shouldPassWhenNoItemMatch() {
        SalesOutbound occupied = new SalesOutbound();
        occupied.setItems(List.of(outboundItem(99L))); // 占用的是别的明细
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of(occupied));

        service.assertSourceSalesOrderItemsNotOccupied(List.of(11L), 5L);
    }

    @Test
    void assertNotOccupied_shouldPassWhenRepositoryEmpty() {
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of());

        service.assertSourceSalesOrderItemsNotOccupied(List.of(11L), 5L);
    }

    // ---------- collectSourceSalesOrderNos ----------

    @Test
    void collectSourceNos_shouldAddResolvedOrderNo() {
        LinkedHashSet<String> nos = new LinkedHashSet<>();
        SalesOrderItem src = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));
        service.collectSourceSalesOrderNos(nos, itemRequest(null, 11L, 5), Map.of(11L, src), 11L);

        assertThat(nos).containsExactly("SO1");
    }

    @Test
    void collectSourceNos_shouldThrowWhenSourceMissing() {
        LinkedHashSet<String> nos = new LinkedHashSet<>();
        assertThatThrownBy(() -> service.collectSourceSalesOrderNos(nos, itemRequest(null, 11L, 5), Map.of(), 11L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void collectSourceNos_shouldAddSourceNoWhenIdNull() {
        LinkedHashSet<String> nos = new LinkedHashSet<>();
        service.collectSourceSalesOrderNos(nos, itemRequest(null, null, 5), Map.of(), null);

        assertThat(nos).containsExactly("SO001"); // 使用 request.sourceNo
    }

    @Test
    void collectSourceNos_shouldSkipWhenSourceNoBlank() {
        LinkedHashSet<String> nos = new LinkedHashSet<>();
        SalesOutboundItemRequest req = new SalesOutboundItemRequest(
                null, "  ", 11L, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                1L, "库房A", "B001", 5, "件", new BigDecimal("1.250"), 100, new BigDecimal("12.500"),
                new BigDecimal("4000"), null);
        service.collectSourceSalesOrderNos(nos, req, Map.of(), null);

        assertThat(nos).isEmpty(); // sourceNo 空白 → trimToNull null → 不添加
    }

    // ---------- resolveItemSourceNo ----------

    @Test
    void resolveItemSourceNo_shouldReturnNullWhenItemSourceNull() {
        assertThat(service.resolveItemSourceNo(outboundItem(null), Map.of())).isNull();
    }

    @Test
    void resolveItemSourceNo_shouldReturnNullWhenMissing() {
        assertThat(service.resolveItemSourceNo(outboundItem(11L), Map.of())).isNull();
    }

    @Test
    void resolveItemSourceNo_shouldReturnOrderNo() {
        SalesOrderItem src = sourceItem(11L, 10, salesOrder(1L, StatusConstants.AUDITED));
        assertThat(service.resolveItemSourceNo(outboundItem(11L), Map.of(11L, src))).isEqualTo("SO1");
    }

    // ---------- 补充边界 ----------

    @Test
    void validate_shouldTreatNullSourceQuantityAsZero() {
        // 来源明细数量 null → 视为 0，任何需求数量都超出
        SalesOrderItem src = sourceItem(11L, null, salesOrder(1L, StatusConstants.AUDITED));
        assertThatThrownBy(() -> validate(itemRequest(null, 11L, 1), src, 11L, "客户A", "项目A", new HashMap<>(), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可出库数量不足");
    }

    @Test
    void collectSourceNos_shouldThrowWhenOrderNull() {
        LinkedHashSet<String> nos = new LinkedHashSet<>();
        SalesOrderItem src = sourceItem(11L, 10, null); // 明细存在但订单为空
        assertThatThrownBy(() -> service.collectSourceSalesOrderNos(nos, itemRequest(null, 11L), Map.of(11L, src), 11L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolveItemSourceNo_shouldReturnNullWhenOrderNull() {
        SalesOrderItem src = sourceItem(11L, 10, null);
        assertThat(service.resolveItemSourceNo(outboundItem(11L), Map.of(11L, src))).isNull();
    }
}
