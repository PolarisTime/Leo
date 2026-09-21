package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MaterialResolver;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购入库合并应用测试：多订单号拼接、多供应商/结算主体拒绝、
 * 表头多仓库（warehouseId 为空、名称为「多仓库」）与单号超长拒绝。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseInboundApplyServiceTest {

    @Mock
    private TradeItemMaterialSupport tradeItemMaterialSupport;

    @Mock
    private PurchaseInboundSourceValidator sourceValidator;

    @Mock
    private PurchaseInboundWeightSettlementService weightSettlementService;

    @Mock
    private InboundItemMapper inboundItemMapper;

    private PurchaseInboundApplyService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseInboundApplyService(
                tradeItemMaterialSupport, sourceValidator, weightSettlementService, inboundItemMapper);
        MaterialResolver materialResolver = mock(MaterialResolver.class);
        when(tradeItemMaterialSupport.prepareResolver()).thenReturn(materialResolver);
        when(materialResolver.resolve(any(), anyString(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        when(weightSettlementService.loadPurchaseWeighCategoryRules(any(PurchaseInboundRequest.class)))
                .thenReturn(Map.of());
        when(weightSettlementService.resolveLineSettlementMode(any(), any(), anyInt())).thenReturn("现结");
        when(weightSettlementService.resolveWeightSettlement(any(), anyInt(), any(), anyString(), eq(true)))
                .thenReturn(new WeightSettlementResult(
                        new BigDecimal("6.25000000"), new BigDecimal("6.25000000"),
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("1.25000000"), new BigDecimal("6.25000000")));
    }

    private PurchaseOrder order(Long id, String orderNo, Long supplierId, Long companyId) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setSupplierId(supplierId);
        order.setSupplierCode("SUP" + supplierId);
        order.setSupplierName("供应商" + supplierId);
        order.setSettlementCompanyId(companyId);
        order.setSettlementCompanyName("结算主体" + companyId);
        return order;
    }

    private PurchaseOrderItem sourceItem(Long id, PurchaseOrder order) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrder(order);
        return item;
    }

    private PurchaseInboundItemRequest line(Long sourceItemId) {
        return new PurchaseInboundItemRequest(
                null, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                sourceItemId, "库房A", "现结", "B001", 5, null,
                new BigDecimal("1.250"), 100, new BigDecimal("6.250"),
                new BigDecimal("6.250"), new BigDecimal("0.000"), new BigDecimal("0.00"),
                new BigDecimal("4000.00"), null);
    }

    private PurchaseInboundRequest request(List<PurchaseInboundItemRequest> items) {
        return new PurchaseInboundRequest(
                "IN001", "PO-HEADER", 1L, "SUP1", "供应商1", null, null,
                LocalDate.of(2026, 8, 1), "现结", StatusConstants.DRAFT, null, items, false);
    }

    private void stubContext(Map<Long, PurchaseOrderItem> sourceMap) {
        List<Long> sourceIds = List.copyOf(sourceMap.keySet());
        when(sourceValidator.prepareContext(any(), any(), any())).thenReturn(
                new PurchaseInboundSourceValidator.SourceValidationContext(
                        sourceIds,
                        sourceIds,
                        sourceMap,
                        new PurchaseInboundAllocationService.AllocationContext(Map.of(), new java.util.HashMap<>())
                ));
    }

    private void stubMapping(java.util.function.BiFunction<Long, Integer, InboundItemMapper.ItemMappingResult> mapper) {
        when(inboundItemMapper.applyItemFields(any(), any(), any(), anyInt(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    PurchaseInboundItem item = invocation.getArgument(2);
                    PurchaseInboundItemRequest source = invocation.getArgument(1);
                    int lineNo = invocation.getArgument(3);
                    item.setLineNo(lineNo);
                    InboundItemMapper.ItemMappingResult result =
                            mapper.apply(source.sourcePurchaseOrderItemId(), lineNo);
                    item.setWarehouseId(result.sourcePurchaseOrderItemId() == null
                            ? null
                            : (result.sourcePurchaseOrderItemId() == 22L ? 8L : 7L));
                    return result;
                });
    }

    private InboundItemMapper.ItemMappingResult mapping(Long sourceItemId, String orderNo, String warehouseName) {
        return new InboundItemMapper.ItemMappingResult(
                orderNo, warehouseName,
                new BigDecimal("6.25000000"), new BigDecimal("25000.00"),
                sourceItemId, BigDecimal.ZERO, new BigDecimal("6.25000000"), 5,
                new BigDecimal("6.25000000"));
    }

    @Test
    void applyItems_shouldJoinPurchaseOrderNosAcrossMultipleOrders() {
        PurchaseOrder first = order(100L, "PO001", 1L, 30L);
        PurchaseOrder second = order(200L, "PO002", 1L, 30L);
        stubContext(Map.of(11L, sourceItem(11L, first), 22L, sourceItem(22L, second)));
        stubMapping((sourceId, lineNo) -> mapping(
                sourceId, sourceId != null && sourceId == 22L ? "PO002" : "PO001", "库房A"));
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setStatus(StatusConstants.DRAFT);

        service.applyItems(inbound, request(List.of(line(11L), line(22L))), () -> 1L);

        assertThat(inbound.getPurchaseOrderNo()).isEqualTo("PO001, PO002");
        verify(sourceValidator).assertPurchaseOrderNoWithinLength("PO001, PO002");
    }

    @Test
    void applyItems_shouldRejectOverlongJoinedPurchaseOrderNo() {
        PurchaseOrder first = order(100L, "PO001", 1L, 30L);
        PurchaseOrder second = order(200L, "PO002", 1L, 30L);
        stubContext(Map.of(11L, sourceItem(11L, first), 22L, sourceItem(22L, second)));
        stubMapping((sourceId, lineNo) -> mapping(sourceId, "PO001", "库房A"));
        doThrow(new BusinessException(
                ErrorCode.VALIDATION_ERROR, "采购入库单采购订单号长度不能超过 256 个字符"))
                .when(sourceValidator).assertPurchaseOrderNoWithinLength(anyString());
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setStatus(StatusConstants.DRAFT);

        assertThatThrownBy(() -> service.applyItems(
                inbound, request(List.of(line(11L), line(22L))), () -> 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("长度不能超过");
    }

    @Test
    void applyItems_shouldRejectDifferentSuppliers() {
        PurchaseOrder first = order(100L, "PO001", 1L, 30L);
        PurchaseOrder second = order(200L, "PO002", 2L, 30L);
        stubContext(Map.of(11L, sourceItem(11L, first), 22L, sourceItem(22L, second)));
        stubMapping((sourceId, lineNo) -> mapping(sourceId, "PO001", "库房A"));
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setStatus(StatusConstants.DRAFT);

        assertThatThrownBy(() -> service.applyItems(
                inbound, request(List.of(line(11L), line(22L))), () -> 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不同供应商");
    }

    @Test
    void applyItems_shouldRejectDifferentSettlementCompanies() {
        PurchaseOrder first = order(100L, "PO001", 1L, 30L);
        PurchaseOrder second = order(200L, "PO002", 1L, 40L);
        stubContext(Map.of(11L, sourceItem(11L, first), 22L, sourceItem(22L, second)));
        when(inboundItemMapper.applyItemFields(any(), any(), any(), anyInt(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    PurchaseInboundItem item = invocation.getArgument(2);
                    PurchaseInboundItemRequest source = invocation.getArgument(1);
                    item.setLineNo(invocation.getArgument(3));
                    item.setWarehouseId(7L);
                    boolean secondLine = source.sourcePurchaseOrderItemId() != null
                            && source.sourcePurchaseOrderItemId() == 22L;
                    item.setSettlementCompanyId(secondLine ? 40L : 30L);
                    item.setSettlementCompanyName(secondLine ? "结算主体40" : "结算主体30");
                    return mapping(source.sourcePurchaseOrderItemId(), "PO001", "库房A");
                });
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setStatus(StatusConstants.DRAFT);

        assertThatThrownBy(() -> service.applyItems(
                inbound, request(List.of(line(11L), line(22L))), () -> 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不同结算主体");
    }

    @Test
    void applyItems_shouldNullHeaderWarehouseAndUseMultiWarehouseName() {
        PurchaseOrder first = order(100L, "PO001", 1L, 30L);
        PurchaseOrder second = order(200L, "PO002", 1L, 30L);
        stubContext(Map.of(11L, sourceItem(11L, first), 22L, sourceItem(22L, second)));
        stubMapping((sourceId, lineNo) -> mapping(sourceId, "PO001", "库房" + sourceId));
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setStatus(StatusConstants.DRAFT);

        service.applyItems(inbound, request(List.of(line(11L), line(22L))), () -> 1L);

        assertThat(inbound.getWarehouseId()).isNull();
        assertThat(inbound.getWarehouseName()).isEqualTo("多仓库");
    }

    @Test
    void applyItems_shouldKeepSingleWarehouseOnHeader() {
        PurchaseOrder order = order(100L, "PO001", 1L, 30L);
        stubContext(Map.of(11L, sourceItem(11L, order)));
        stubMapping((sourceId, lineNo) -> mapping(sourceId, "PO001", "库房A"));
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setStatus(StatusConstants.DRAFT);

        service.applyItems(inbound, request(List.of(line(11L))), () -> 1L);

        assertThat(inbound.getWarehouseId()).isEqualTo(7L);
        assertThat(inbound.getWarehouseName()).isEqualTo("库房A");
    }

    @Test
    void applyItems_shouldRejectHeaderSupplierCodeMismatchAcrossOrders() {
        PurchaseOrder first = order(100L, "PO001", 1L, 30L);
        first.setSupplierCode("SUP-A");
        PurchaseOrder second = order(200L, "PO002", 1L, 30L);
        second.setSupplierCode("SUP-B");
        stubContext(Map.of(11L, sourceItem(11L, first), 22L, sourceItem(22L, second)));
        stubMapping((sourceId, lineNo) -> mapping(sourceId, "PO001", "库房A"));
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setStatus(StatusConstants.DRAFT);

        assertThatThrownBy(() -> service.applyItems(
                inbound, request(List.of(line(11L), line(22L))), () -> 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不同供应商");
    }
}
