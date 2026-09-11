package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderPickupListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderPickupListServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    private PurchaseOrderPickupListService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderPickupListService(purchaseOrderRepository);
    }

    private PurchaseOrderItem item(Long id, Integer lineNo, String warehouseName,
                                   Integer quantity, String weightTon) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setLineNo(lineNo);
        item.setWarehouseName(warehouseName);
        item.setQuantity(quantity);
        item.setWeightTon(weightTon == null ? null : new BigDecimal(weightTon));
        return item;
    }

    private PurchaseOrder order(Long id, String orderNo, Long supplierId, String supplierName,
                                Long companyId, String companyName, List<PurchaseOrderItem> items) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setSupplierId(supplierId);
        order.setSupplierName(supplierName);
        order.setSettlementCompanyId(companyId);
        order.setSettlementCompanyName(companyName);
        order.setItems(new ArrayList<>(items));
        return order;
    }

    private PurchaseOrder simpleOrder(Long id, String orderNo) {
        return order(id, orderNo, 1L, "供应商A", 10L, "主体A",
                List.of(item(id * 100, 1, "仓库A", 1, "1.0")));
    }

    @Test
    void preview_shouldRejectNullAndEmptySelection() {
        assertThatThrownBy(() -> service.preview(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请至少选择一张采购订单");
        assertThatThrownBy(() -> service.preview(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请至少选择一张采购订单");
    }

    @Test
    void preview_shouldRejectMoreThanFiftyOrders() {
        List<Long> tooMany = IntStream.rangeClosed(1, 51).mapToObj(Long::valueOf).toList();

        assertThatThrownBy(() -> service.preview(tooMany))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单次最多查看 50 张采购订单");
    }

    @Test
    void preview_shouldDeduplicateRequestedOrderIds() {
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L)))
                .thenReturn(List.of(simpleOrder(1L, "PO1"), simpleOrder(2L, "PO2")));

        PurchaseOrderPickupListResponse response = service.preview(List.of(1L, 1L, 2L));

        assertThat(response.orderCount()).isEqualTo(2);
        verify(purchaseOrderRepository).findByIdInAndDeletedFlagFalse(List.of(1L, 2L));
    }

    @Test
    void preview_shouldRejectWhenSomeOrdersMissing() {
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L)))
                .thenReturn(List.of(simpleOrder(1L, "PO1")));

        assertThatThrownBy(() -> service.preview(List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部分采购订单不存在或已删除");
    }

    @Test
    void preview_shouldRejectWhenNoItemsAtAll() {
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(1L)))
                .thenReturn(List.of(order(1L, "PO1", 1L, "供应商A", 10L, "主体A", List.of())));

        assertThatThrownBy(() -> service.preview(List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所选采购订单没有明细");
    }

    @Test
    void preview_shouldGroupBySupplierAndSettlementCompany() {
        PurchaseOrder first = simpleOrder(1L, "PO1");
        PurchaseOrder second = simpleOrder(2L, "PO2");
        PurchaseOrder otherSupplier = order(3L, "PO3", 2L, "供应商B", 10L, "主体A",
                List.of(item(300L, 1, "仓库A", 1, "1.0")));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(first, second, otherSupplier));

        PurchaseOrderPickupListResponse response = service.preview(List.of(1L, 2L, 3L));

        assertThat(response.groups()).hasSize(2);
        assertThat(response.supplierCount()).isEqualTo(2);
        assertThat(response.orderCount()).isEqualTo(3);
        assertThat(response.groups().get(0).orderCount()).isEqualTo(2);
        assertThat(response.groups().get(0).itemCount()).isEqualTo(2);
    }

    @Test
    void preview_shouldWarnWhenOrderHasNoItems() {
        PurchaseOrder withItems = simpleOrder(1L, "PO1");
        PurchaseOrder withoutItems = order(2L, "PO2", 1L, "供应商A", 10L, "主体A", List.of());
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L)))
                .thenReturn(List.of(withItems, withoutItems));

        PurchaseOrderPickupListResponse response = service.preview(List.of(1L, 2L));

        assertThat(response.warnings()).containsExactly("采购订单 PO2 无明细");
        assertThat(response.groups()).hasSize(1);
    }

    @Test
    void preview_shouldComputeTotals() {
        PurchaseOrder order = order(1L, "PO1", 1L, "供应商A", 10L, "主体A",
                List.of(item(100L, 1, "仓库A", 3, "1.5"), item(101L, 2, "仓库A", 5, "2.0")));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(1L)))
                .thenReturn(List.of(order));

        PurchaseOrderPickupListResponse response = service.preview(List.of(1L));

        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.totalQuantity()).isEqualTo(8);
        assertThat(response.totalWeightTon()).isEqualByComparingTo("3.5");
        assertThat(response.groups().get(0).totalQuantity()).isEqualTo(8);
        assertThat(response.groups().get(0).totalWeightTon()).isEqualByComparingTo("3.5");
    }

    @Test
    void preview_shouldSortItemsByWarehouseThenLineNo() {
        PurchaseOrder order = order(1L, "PO1", 1L, "供应商A", 10L, "主体A", List.of(
                item(100L, 1, "仓库B", 1, "1.0"),
                item(101L, 2, "仓库A", 1, "1.0"),
                item(102L, 1, "仓库A", 1, "1.0")));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(1L)))
                .thenReturn(List.of(order));

        PurchaseOrderPickupListResponse response = service.preview(List.of(1L));

        List<PurchaseOrderPickupListResponse.Item> items = response.groups().get(0).items();
        assertThat(items).extracting(PurchaseOrderPickupListResponse.Item::warehouseName)
                .containsExactly("仓库A", "仓库A", "仓库B");
        assertThat(items.get(0).lineNo()).isEqualTo(1);
        assertThat(items.get(1).lineNo()).isEqualTo(2);
    }
}
