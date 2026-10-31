package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.api.PurchaseSupplierLedgerLock;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderDirectSalesCapacityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「采购订单明细是否收满」单一口径的真实 PostgreSQL 回归（默认跳过，设置
 * {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>场景：订货 100 / 草稿兄弟单 50 / 本单已审核 50。旧实现里入库单侧把草稿计入
 * （DB 汇总不过滤状态 + 内存数量）会得到 100 并把本单误置「完成入库」，
 * 而订单侧只统计有效状态仍是 50。本用例锁定收敛后的唯一口径：草稿不参与，
 * 两侧都停在「已审核」；把兄弟单改为已审核后两侧同时完成。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class PurchaseInboundCompletionConvergencePostgresTest {

    private static final long SUPPLIER_ID = 944000000000000001L;
    private static final long WAREHOUSE_ID = 944000000000000002L;
    private static final long MATERIAL_ID = 944000000000000003L;
    private static final long ORDER_ID = 944000000000000010L;
    private static final long ORDER_ITEM_ID = 944000000000000011L;
    private static final long SIBLING_INBOUND_ID = 944000000000000020L;
    private static final long CURRENT_INBOUND_ID = 944000000000000021L;

    @Autowired
    private PurchaseInboundRepository purchaseInboundRepository;

    @Autowired
    private PurchaseInboundItemRepository purchaseInboundItemRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private PurchaseInboundItemQueryService queryService;
    private PurchaseInboundCompletionSyncService service;
    private PurchaseOrder order;

    @BeforeEach
    void setUp() {
        queryService = new PurchaseInboundItemQueryService(purchaseInboundItemRepository);
        PurchaseInboundSourceValidator sourceValidator = mock(PurchaseInboundSourceValidator.class);
        order = new PurchaseOrder();
        order.setId(ORDER_ID);
        order.setStatus(StatusConstants.AUDITED);
        order.setSettlementCompanyId(1L);
        order.setSupplierId(SUPPLIER_ID);
        order.setItems(new ArrayList<>());
        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(ORDER_ITEM_ID);
        sourceItem.setQuantity(100);
        sourceItem.setPurchaseOrder(order);
        order.getItems().add(sourceItem);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(ORDER_ITEM_ID)))
                .thenReturn(Map.of(ORDER_ITEM_ID, sourceItem));

        PurchaseOrderDirectSalesCapacityGuard guard =
                new PurchaseOrderDirectSalesCapacityGuard(null, queryService);
        service = new PurchaseInboundCompletionSyncService(
                sourceValidator,
                queryService,
                mock(PurchaseSupplierLedgerLock.class),
                guard,
                mock(PurchaseOrderAuditPublisher.class)
        );
        seedMasterData();
        seedPurchaseOrder();
    }

    @Test
    void draftSibling_shouldBeExcludedFromBothInboundAndOrderCompletion() {
        seedInbound(SIBLING_INBOUND_ID, StatusConstants.DRAFT, 50);
        seedInbound(CURRENT_INBOUND_ID, StatusConstants.AUDITED, 50);
        purchaseInboundRepository.flush();

        // 唯一 SQL + 有效状态白名单：草稿兄弟单不产生有效入库累计。
        assertThat(queryService.summarizeEffectiveQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                List.of(ORDER_ITEM_ID), CURRENT_INBOUND_ID)).isEmpty();
        assertThat(queryService.summarizeEffectiveQuantityBySourcePurchaseOrderItemIds(
                List.of(ORDER_ITEM_ID))).containsExactlyEntriesOf(Map.of(ORDER_ITEM_ID, 50L));

        PurchaseInbound current = purchaseInboundRepository.findById(CURRENT_INBOUND_ID).orElseThrow();
        // 订货 100 / 草稿 50 / 本单 50：本单不得被误判「完成入库」。
        assertThat(service.shouldCompleteInbound(current)).isFalse();

        // 订单侧同一口径：有效累计 50 ≠ 100，保持已审核。
        service.synchronizeSourcePurchaseOrders(current, false);
        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED);
    }

    @Test
    void auditedSibling_shouldCompleteBothInboundAndOrder() {
        seedInbound(SIBLING_INBOUND_ID, StatusConstants.AUDITED, 50);
        seedInbound(CURRENT_INBOUND_ID, StatusConstants.AUDITED, 50);
        purchaseInboundRepository.flush();

        PurchaseInbound current = purchaseInboundRepository.findById(CURRENT_INBOUND_ID).orElseThrow();
        // 兄弟单已审核 50 + 本单内存 50 = 100 → 本单完成入库。
        assertThat(service.shouldCompleteInbound(current)).isTrue();

        service.synchronizeSourcePurchaseOrders(current, false);
        // 订单侧同一 SQL 累计 100 == 订货 100 → 完成采购。
        assertThat(order.getStatus()).isEqualTo(StatusConstants.PURCHASE_COMPLETED);
    }

    private void seedMasterData() {
        jdbc.update("""
                insert into md_supplier (id, supplier_code, supplier_name, status)
                values (?, ?, 'PG完成度供应商', '正常')
                """, SUPPLIER_ID, "PG-CONV-S-" + SUPPLIER_ID);
        jdbc.update("""
                insert into md_warehouse (id, warehouse_code, warehouse_name, warehouse_type, status)
                values (?, ?, 'PG完成度仓库', '自营', '正常')
                """, WAREHOUSE_ID, "PG-CONV-W-" + WAREHOUSE_ID);
        jdbc.update("""
                insert into md_material
                    (id, material_code, brand, material, category, spec, unit,
                     piece_weight_ton, pieces_per_bundle, unit_price)
                values (?, ?, 'PG品牌', 'PG材质', 'PG类别', 'PG规格', '吨', 1.00000000, 10, 100.00)
                """, MATERIAL_ID, "PG-CONV-M-" + MATERIAL_ID);
    }

    private void seedPurchaseOrder() {
        jdbc.update("""
                insert into po_purchase_order
                    (id, order_no, supplier_name, order_date, total_weight, total_amount, status,
                     supplier_code, supplier_id)
                values (?, ?, 'PG完成度供应商', now(), 3.00000000, 200.00, '已审核', ?, ?)
                """, ORDER_ID, String.valueOf(ORDER_ID), "PG-CONV-S-" + SUPPLIER_ID, SUPPLIER_ID);
        jdbc.update("""
                insert into po_purchase_order_item
                    (id, order_id, line_no, material_code, brand, category, material, spec, unit,
                     quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                     material_id, warehouse_id, warehouse_name)
                values (?, ?, 1, ?, 'PG品牌', 'PG类别', 'PG材质', 'PG规格', '吨',
                        100, 1.00000000, 10, 1.00000000, 100.00, 10000.00, ?, ?, 'PG完成度仓库')
                """, ORDER_ITEM_ID, ORDER_ID, "PG-CONV-M-" + MATERIAL_ID, MATERIAL_ID, WAREHOUSE_ID);
    }

    private void seedInbound(long inboundId, String status, int quantity) {
        jdbc.update("""
                insert into po_purchase_inbound
                    (id, inbound_no, supplier_name, warehouse_name, inbound_date, settlement_mode,
                     total_weight, total_amount, status, supplier_code, supplier_id, deleted_flag)
                values (?, ?, 'PG完成度供应商', 'PG完成度仓库', now(), '过磅', 1.00000000, 5000.00, ?, ?, ?, false)
                """, inboundId, String.valueOf(inboundId), status, "PG-CONV-S-" + SUPPLIER_ID, SUPPLIER_ID);
        jdbc.update("""
                insert into po_purchase_inbound_item
                    (id, inbound_id, line_no, material_code, brand, category, material, spec, unit,
                     quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                     source_purchase_order_item_id, settlement_mode, material_id, warehouse_id)
                values (?, ?, 1, ?, 'PG品牌', 'PG类别', 'PG材质', 'PG规格', '吨',
                        ?, 1.00000000, 10, 1.00000000, 100.00, 5000.00, ?, '过磅', ?, ?)
                """, inboundId + 1, inboundId, "PG-CONV-M-" + MATERIAL_ID, quantity,
                ORDER_ITEM_ID, MATERIAL_ID, WAREHOUSE_ID);
    }
}
