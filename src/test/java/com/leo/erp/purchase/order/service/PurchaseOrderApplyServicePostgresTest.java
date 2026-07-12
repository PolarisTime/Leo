package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemMaterialSupportTestDoubles;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.testsupport.StableIdentityPostgresFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
class PurchaseOrderApplyServicePostgresTest {

    private static final long ORDER_ID = 8_810_000_000_000_000_001L;
    private static final long EXISTING_ITEM_ID = 8_810_000_000_000_000_101L;
    private static final long NEW_ITEM_ID = 8_810_000_000_000_000_102L;
    private static final long REMOVED_ITEM_ID = 8_810_000_000_000_000_103L;
    private static final long SUPPLIER_ID = 8_810_000_000_000_000_201L;
    private static final long MATERIAL_1_ID = 8_810_000_000_000_000_202L;
    private static final long MATERIAL_2_ID = 8_810_000_000_000_000_203L;
    private static final long MATERIAL_3_ID = 8_810_000_000_000_000_204L;
    private static final long WAREHOUSE_ID = 8_810_000_000_000_000_205L;
    private static final long SETTLEMENT_COMPANY_ID = 8_810_000_000_000_000_206L;
    private static final String SUPPLIER_CODE = "TEST-ORDER-SUPPLIER";

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseInboundItemRepository purchaseInboundItemRepository;

    @Autowired
    private ItemAllocationNativeRepository itemAllocationNativeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertStableIdentityFixtures() {
        StableIdentityPostgresFixtures.insertSupplier(
                jdbcTemplate, SUPPLIER_ID, SUPPLIER_CODE, "测试供应商");
        StableIdentityPostgresFixtures.insertMaterial(jdbcTemplate, MATERIAL_1_ID, "M1");
        StableIdentityPostgresFixtures.insertMaterial(jdbcTemplate, MATERIAL_2_ID, "M2");
        StableIdentityPostgresFixtures.insertMaterial(jdbcTemplate, MATERIAL_3_ID, "M3");
        StableIdentityPostgresFixtures.insertWarehouse(
                jdbcTemplate, WAREHOUSE_ID, "TEST-ORDER-WAREHOUSE", "测试仓库");
        StableIdentityPostgresFixtures.insertSettlementCompany(
                jdbcTemplate, SETTLEMENT_COMPANY_ID, "测试结算主体");
    }

    @Test
    void shouldInitializeNewItemBeforeNativeQueryFlushesManagedOrder() {
        purchaseOrderRepository.saveAndFlush(existingOrder());
        entityManager.clear();

        PurchaseOrder managedOrder = purchaseOrderRepository.findByIdAndDeletedFlagFalse(ORDER_ID)
                .orElseThrow();
        PurchaseOrderApplyService service = applyService();

        service.applyItems(
                managedOrder,
                request(List.of(
                        itemRequest(EXISTING_ITEM_ID, "M1", 10, "1.000", "4000.00"),
                        itemRequest(null, "M2", 5, "1.000", "5000.00")
                )),
                new AtomicLong(NEW_ITEM_ID)::getAndIncrement
        );

        assertThat(itemAllocationNativeRepository.summarizeSalesByPurchaseOrderItems(
                List.of(EXISTING_ITEM_ID, NEW_ITEM_ID),
                null
        )).isEmpty();
        entityManager.flush();
        entityManager.clear();

        PurchaseOrder persistedOrder = purchaseOrderRepository.findByIdAndDeletedFlagFalse(ORDER_ID)
                .orElseThrow();
        assertThat(persistedOrder.getItems())
                .extracting(PurchaseOrderItem::getId)
                .containsExactly(EXISTING_ITEM_ID, NEW_ITEM_ID);
        assertThat(persistedOrder.getItems().get(1).getPurchaseOrder()).isSameAs(persistedOrder);
        assertThat(persistedOrder.getItems().get(1).getMaterialCode()).isEqualTo("M2");
    }

    @Test
    void shouldDeleteItemOmittedFromRequest() {
        purchaseOrderRepository.saveAndFlush(existingOrderWithRemovableItem());
        entityManager.clear();

        PurchaseOrder managedOrder = purchaseOrderRepository.findByIdAndDeletedFlagFalse(ORDER_ID)
                .orElseThrow();
        applyService().applyItems(
                managedOrder,
                request(List.of(itemRequest(EXISTING_ITEM_ID, "M1", 10, "1.000", "4000.00"))),
                new AtomicLong(NEW_ITEM_ID)::getAndIncrement
        );

        entityManager.flush();
        entityManager.clear();

        PurchaseOrder persistedOrder = purchaseOrderRepository.findByIdAndDeletedFlagFalse(ORDER_ID)
                .orElseThrow();
        assertThat(persistedOrder.getItems())
                .extracting(PurchaseOrderItem::getId)
                .containsExactly(EXISTING_ITEM_ID);
        assertThat(entityManager.find(PurchaseOrderItem.class, REMOVED_ITEM_ID)).isNull();
    }

    @Test
    void shouldDeleteExistingItemAndAddInitializedItemAtomically() {
        purchaseOrderRepository.saveAndFlush(existingOrderWithRemovableItem());
        entityManager.clear();

        PurchaseOrder managedOrder = purchaseOrderRepository.findByIdAndDeletedFlagFalse(ORDER_ID)
                .orElseThrow();
        applyService().applyItems(
                managedOrder,
                request(List.of(
                        itemRequest(EXISTING_ITEM_ID, "M1", 10, "1.000", "4000.00"),
                        itemRequest(null, "M2", 5, "1.000", "5000.00")
                )),
                new AtomicLong(NEW_ITEM_ID)::getAndIncrement
        );

        assertThat(itemAllocationNativeRepository.summarizeSalesByPurchaseOrderItems(
                List.of(EXISTING_ITEM_ID, NEW_ITEM_ID),
                null
        )).isEmpty();
        entityManager.flush();
        entityManager.clear();

        PurchaseOrder persistedOrder = purchaseOrderRepository.findByIdAndDeletedFlagFalse(ORDER_ID)
                .orElseThrow();
        assertThat(persistedOrder.getItems())
                .extracting(PurchaseOrderItem::getId)
                .containsExactly(EXISTING_ITEM_ID, NEW_ITEM_ID);
        assertThat(entityManager.find(PurchaseOrderItem.class, REMOVED_ITEM_ID)).isNull();
    }

    private PurchaseOrderApplyService applyService() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSupport = mock(WarehouseSelectionSupport.class);
        when(materialSupport.loadMaterialMap(List.of("M1", "M2"))).thenReturn(Map.of(
                "M1", new TradeMaterialSnapshot(MATERIAL_1_ID, "M1", true),
                "M2", new TradeMaterialSnapshot(MATERIAL_2_ID, "M2", true)
        ));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of(
                "M1", new TradeMaterialSnapshot(MATERIAL_1_ID, "M1", true)
        ));
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(materialSupport);
        when(materialSupport.normalizeBatchNo(any(), any(), anyInt(), eq(false)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(warehouseSupport.resolveWarehouse(any(), any(), anyInt(), eq(true)))
                .thenAnswer(invocation -> {
                    entityManager.flush();
                    return new WarehouseSnapshot(
                            WAREHOUSE_ID,
                            "TEST-ORDER-WAREHOUSE",
                            "测试仓库"
                    );
                });
        return new PurchaseOrderApplyService(
                materialSupport,
                warehouseSupport,
                new PurchaseInboundItemQueryService(purchaseInboundItemRepository, null)
        );
    }

    private PurchaseOrder existingOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(ORDER_ID);
        order.setOrderNo("TEST-PO-AUTO-FLUSH");
        order.setSupplierId(SUPPLIER_ID);
        order.setSupplierCode(SUPPLIER_CODE);
        order.setSupplierName("测试供应商");
        order.setOrderDate(LocalDateTime.of(2026, 7, 10, 14, 0));
        order.setSettlementCompanyId(SETTLEMENT_COMPANY_ID);
        order.setSettlementCompanyName("测试结算主体");
        order.setStatus("草稿");
        order.setTotalWeight(new BigDecimal("1.000"));
        order.setTotalAmount(new BigDecimal("4000.00"));
        order.setCreatedBy(0L);
        order.setCreatedAt(LocalDateTime.of(2026, 7, 10, 14, 0));

        PurchaseOrderItem item = item(EXISTING_ITEM_ID, order, 1, "M1", 10, "1.000", "4000.00");
        order.getItems().add(item);
        return order;
    }

    private PurchaseOrder existingOrderWithRemovableItem() {
        PurchaseOrder order = existingOrder();
        order.getItems().add(item(REMOVED_ITEM_ID, order, 2, "M3", 2, "1.000", "3000.00"));
        order.setTotalWeight(new BigDecimal("2.000"));
        order.setTotalAmount(new BigDecimal("7000.00"));
        return order;
    }

    private PurchaseOrderRequest request(List<PurchaseOrderItemRequest> items) {
        return new PurchaseOrderRequest(
                "TEST-PO-AUTO-FLUSH",
                SUPPLIER_ID,
                SUPPLIER_CODE,
                "测试供应商",
                LocalDateTime.of(2026, 7, 10, 14, 0),
                "测试采购员",
                SETTLEMENT_COMPANY_ID,
                "草稿",
                null,
                items
        );
    }

    private PurchaseOrderItemRequest itemRequest(Long id,
                                                 String materialCode,
                                                 int quantity,
                                                 String weightTon,
                                                 String unitPrice) {
        BigDecimal weight = new BigDecimal(weightTon);
        BigDecimal price = new BigDecimal(unitPrice);
        return new PurchaseOrderItemRequest(
                id,
                materialId(materialCode),
                materialCode,
                "测试品牌",
                "测试类别",
                "测试材质",
                "TEST-SPEC",
                "12m",
                "吨",
                WAREHOUSE_ID,
                "测试仓库",
                "TEST-BATCH",
                quantity,
                "件",
                weight.divide(BigDecimal.valueOf(quantity)),
                1,
                weight,
                price,
                weight.multiply(price)
        );
    }

    private PurchaseOrderItem item(Long id,
                                   PurchaseOrder order,
                                   int lineNo,
                                   String materialCode,
                                   int quantity,
                                   String weightTon,
                                   String unitPrice) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        PurchaseOrderItemRequest request = itemRequest(id, materialCode, quantity, weightTon, unitPrice);
        item.setId(id);
        item.setPurchaseOrder(order);
        item.setLineNo(lineNo);
        item.setMaterialId(materialId(materialCode));
        item.setMaterialCode(request.materialCode());
        item.setBrand(request.brand());
        item.setCategory(request.category());
        item.setMaterial(request.material());
        item.setSpec(request.spec());
        item.setLength(request.length());
        item.setUnit(request.unit());
        item.setWarehouseId(WAREHOUSE_ID);
        item.setWarehouseName(request.warehouseName());
        item.setBatchNo(request.batchNo());
        item.setQuantity(request.quantity());
        item.setQuantityUnit(request.quantityUnit());
        item.setPieceWeightTon(request.pieceWeightTon());
        item.setPiecesPerBundle(request.piecesPerBundle());
        item.setWeightTon(request.weightTon());
        item.setUnitPrice(request.unitPrice());
        item.setAmount(request.amount());
        return item;
    }

    private long materialId(String materialCode) {
        return switch (materialCode) {
            case "M1" -> MATERIAL_1_ID;
            case "M2" -> MATERIAL_2_ID;
            case "M3" -> MATERIAL_3_ID;
            default -> throw new IllegalArgumentException("未知测试商品编码: " + materialCode);
        };
    }
}
