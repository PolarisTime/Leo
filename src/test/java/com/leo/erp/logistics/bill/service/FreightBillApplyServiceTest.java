package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
import com.leo.erp.logistics.bill.repository.FreightBillSourceOrderRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.sales.api.SalesOrderSourceItemSnapshot;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FreightBillApplyService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class FreightBillApplyServiceTest {

    @Mock
    private SalesOrderLogisticsSourceQuery salesOrderSourceQuery;

    @Mock
    private FreightBillSourceOrderRepository sourceOrderRepository;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @InjectMocks
    private FreightBillApplyService service;

    private SalesOrderSourceItemSnapshot srcItem(Long id, String weightTon) {
        return new SalesOrderSourceItemSnapshot(
                id, 1, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 30L, "结算公司A", 1L, "库房A", "B001", "b001", 10, "件",
                new BigDecimal("1.250"), 100,
                weightTon == null ? null : new BigDecimal(weightTon),
                new BigDecimal("4000"), new BigDecimal("50000"),
                weightTon == null ? null : new BigDecimal(weightTon));
    }

    private SalesOrderSourceSnapshot srcOrder(Long id, String orderNo, String status,
                                              List<SalesOrderSourceItemSnapshot> items) {
        return new SalesOrderSourceSnapshot(
                id, orderNo, "IB001", "PO001", "CUST001", 10L, "客户A", 20L, "项目A", 30L, "结算公司A",
                LocalDate.of(2026, 8, 1), "销售员A", new BigDecimal("100"), new BigDecimal("5000"),
                status, false, null, items);
    }

    private FreightBillItemRequest itemReq(Long id, Long sourceId) {
        return new FreightBillItemRequest(
                id, "SO001", 30L, "结算公司A", 10L, "客户A", 20L, "项目A", 500L, "M001", "螺纹钢",
                "品牌A", "型钢", "螺纹钢", "HRB400", "12m", 10, "件", new BigDecimal("1.250"), 100,
                "B001", new BigDecimal("12.500"), 1L, "库房A", null, null, sourceId);
    }

    private FreightBillRequest request(List<FreightBillItemRequest> items) {
        return new FreightBillRequest("FB001", 1L, "C001", "承运商A", 30L, "结算公司A", null, null,
                LocalDate.of(2026, 8, 1), new BigDecimal("100"), "DRAFT", null, items, List.of(), false);
    }

    private void stubSources(SalesOrderSourceSnapshot order) {
        when(salesOrderSourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        // 多数校验分支在占用检查之前抛错，此 stub 可能未使用
        lenient().when(sourceOrderRepository.findOccupiedSourceOrderIds(any(), any())).thenReturn(List.of());
    }

    // ---------- 正常路径 ----------

    @Test
    void applyItems_shouldApplyItemsAndSyncSources() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "12.500"))));
        FreightBill entity = new FreightBill();

        service.applyItems(entity, request(List.of(itemReq(null, 11L))), () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getLineNo()).isEqualTo(1);
        assertThat(entity.getItems().get(0).getWeightTon()).isEqualByComparingTo("12.500");
        assertThat(entity.getItems().get(0).getMaterialName()).isEqualTo("品牌A");
        assertThat(entity.getTotalWeight()).isEqualByComparingTo("12.500");
        assertThat(entity.getTotalFreight()).isEqualByComparingTo("1250.00"); // 12.5 * 100
        assertThat(entity.getSourceOrders()).hasSize(1);
        assertThat(entity.getSourceOrders().iterator().next().getSourceSalesOrderId()).isEqualTo(1L);
    }

    @Test
    void applyItems_shouldSkipExistingSourceRelation() {
        FreightBill entity = new FreightBill();
        FreightBillSourceOrder existing = new FreightBillSourceOrder();
        existing.setSourceSalesOrderId(1L);
        existing.setSourceSalesOrderNo("SO001");
        existing.setActiveFlag(true);
        entity.getSourceOrders().add(existing);
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "12.500"))));

        service.applyItems(entity, request(List.of(itemReq(null, 11L))), () -> 100L);

        assertThat(entity.getSourceOrders()).hasSize(1); // 已存在来源不重复添加
    }

    // ---------- 校验失败 ----------

    @Test
    void applyItems_shouldRejectNullOrDuplicateSourceItemId() {
        FreightBillRequest request = request(List.of(itemReq(null, null), itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整单导入");
    }

    @Test
    void applyItems_shouldRejectPartialImport() {
        SalesOrderSourceSnapshot order = srcOrder(1L, "SO001", StatusConstants.AUDITED,
                List.of(srcItem(11L, "1.000"), srcItem(12L, "2.000")));
        when(salesOrderSourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("全部明细");
    }

    @Test
    void applyItems_shouldRejectEmptyOrders() {
        when(salesOrderSourceQuery.findBySourceItemIds(any())).thenReturn(List.of());
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要导入");
    }

    @Test
    void applyItems_shouldRejectInvalidSourceStatus() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.DRAFT, List.of(srcItem(11L, "12.500"))));
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不能生成物流单");
    }

    @Test
    void applyItems_shouldRejectNonPositiveWeight() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "0"))));
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重量小于等于0");
    }

    @Test
    void applyItems_shouldRejectNegativeWeight() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "-1.000"))));
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重量小于等于0");
    }

    @Test
    void applyItems_shouldRejectChangedSourceSet() {
        FreightBill entity = new FreightBill();
        FreightBillSourceOrder rel = new FreightBillSourceOrder();
        rel.setSourceSalesOrderId(2L); // 已有来源 2，请求来源 1
        rel.setActiveFlag(true);
        entity.getSourceOrders().add(rel);
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "12.500"))));
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity, request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保存后不能新增");
    }

    @Test
    void applyItems_shouldRejectOccupiedSourceOrder() {
        when(salesOrderSourceQuery.findBySourceItemIds(any()))
                .thenReturn(List.of(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "12.500")))));
        when(sourceOrderRepository.findOccupiedSourceOrderIds(any(), any())).thenReturn(List.of(1L));
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已关联其他物流单");
    }

    @Test
    void applyItems_shouldSkipOccupiedCheckWhenNoExistingSourceOrders() {
        // entity 无来源（空集）→ assertSourceSetImmutable 通过；occupied 空 → 正常
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "12.500"))));
        FreightBill entity = new FreightBill();

        service.applyItems(entity, request(List.of(itemReq(null, 11L))), () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
        verify(sourceOrderRepository).findOccupiedSourceOrderIds(any(), any());
    }
}
