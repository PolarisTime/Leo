package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.service.SalesOrderPurchaseReferenceAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

/**
 * 真实 PostgreSQL 采购入库明细引用释放回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>覆盖: 软删入库单释放未被销售订单物理引用的明细行后, 来源采购订单明细物理删除不再被
 * {@code fk_po_purchase_inbound_item_source_identity} 拒绝; 仍被销售订单明细引用的行保留,
 * 继续由 RESTRICT 外键保护。</p>
 * <p>用例自带隔离主数据与单据(测试事务回滚), 不依赖开发库既有业务数据。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class PurchaseInboundItemReferenceReleasePostgresTest {

    private static final long CUSTOMER_ID = 943000000000000001L;
    private static final long PROJECT_ID = 943000000000000002L;
    private static final long SUPPLIER_ID = 943000000000000003L;
    private static final long WAREHOUSE_ID = 943000000000000004L;
    private static final long MATERIAL_ID = 943000000000000005L;
    private static final long ORDER_ID = 943000000000000010L;
    private static final long ORDER_ITEM_RELEASABLE = 943000000000000011L;
    private static final long ORDER_ITEM_RETAINED = 943000000000000012L;
    private static final long INBOUND_ID = 943000000000000020L;
    private static final long INBOUND_ITEM_RELEASABLE = 943000000000000021L;
    private static final long INBOUND_ITEM_RETAINED = 943000000000000022L;
    private static final long SALES_ORDER_ID = 943000000000000030L;
    private static final long SALES_ORDER_ITEM = 943000000000000031L;

    @Autowired
    private PurchaseInboundRepository purchaseInboundRepository;

    @Autowired
    private PurchaseInboundItemRepository purchaseInboundItemRepository;

    @Autowired
    private SalesOrderItemRepository salesOrderItemRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private PurchaseInboundDeleteService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseInboundDeleteService(
                mock(PurchaseInboundWeightWriteBackService.class),
                purchaseInboundItemRepository,
                new SalesOrderPurchaseReferenceAdapter(salesOrderItemRepository)
        );
    }

    @Test
    void afterDelete_shouldPhysicallyReleaseOnlyUnreferencedItemReferences() {
        seedMasterData();
        seedPurchaseDocuments();
        seedSalesOrderReferencingRetainedItem();

        service.afterDelete(purchaseInboundRepository.findById(INBOUND_ID).orElseThrow());
        purchaseInboundItemRepository.flush();

        assertThat(itemExists(INBOUND_ITEM_RELEASABLE)).isFalse();
        assertThat(itemExists(INBOUND_ITEM_RETAINED)).isTrue();

        jdbc.update("delete from po_purchase_order_item where id = ?", ORDER_ITEM_RELEASABLE);
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update(
                        "delete from po_purchase_order_item where id = ?", ORDER_ITEM_RETAINED));
    }

    private boolean itemExists(long itemId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from po_purchase_inbound_item where id = ?", Integer.class, itemId);
        return count != null && count > 0;
    }

    private void seedMasterData() {
        jdbc.update("""
                insert into md_customer (id, customer_code, customer_name, project_name, status)
                values (?, ?, 'PG客户', 'PG项目', '正常')
                """, CUSTOMER_ID, "PG-C-" + CUSTOMER_ID);
        jdbc.update("""
                insert into md_project (id, project_code, project_name, customer_code, customer_id, status)
                values (?, ?, 'PG项目', ?, ?, '正常')
                """, PROJECT_ID, "PG-P-" + PROJECT_ID, "PG-C-" + CUSTOMER_ID, CUSTOMER_ID);
        jdbc.update("""
                insert into md_supplier (id, supplier_code, supplier_name, status)
                values (?, ?, 'PG供应商', '正常')
                """, SUPPLIER_ID, "PG-S-" + SUPPLIER_ID);
        jdbc.update("""
                insert into md_warehouse (id, warehouse_code, warehouse_name, warehouse_type, status)
                values (?, ?, 'PG仓库', '自营', '正常')
                """, WAREHOUSE_ID, "PG-W-" + WAREHOUSE_ID);
        jdbc.update("""
                insert into md_material
                    (id, material_code, brand, material, category, spec, unit,
                     piece_weight_ton, pieces_per_bundle, unit_price)
                values (?, ?, 'PG品牌', 'PG材质', 'PG类别', 'PG规格', '吨', 1.00000000, 10, 100.00)
                """, MATERIAL_ID, "PG-M-" + MATERIAL_ID);
    }

    private void seedPurchaseDocuments() {
        jdbc.update("""
                insert into po_purchase_order
                    (id, order_no, supplier_name, order_date, total_weight, total_amount, status,
                     supplier_code, supplier_id)
                values (?, ?, 'PG供应商', now(), 3.00000000, 200.00, '草稿', ?, ?)
                """, ORDER_ID, String.valueOf(ORDER_ID), "PG-S-" + SUPPLIER_ID, SUPPLIER_ID);
        insertOrderItem(ORDER_ITEM_RELEASABLE, 1);
        insertOrderItem(ORDER_ITEM_RETAINED, 2);
        jdbc.update("""
                insert into po_purchase_inbound
                    (id, inbound_no, supplier_name, warehouse_name, inbound_date, settlement_mode,
                     total_weight, total_amount, status, supplier_code, supplier_id, deleted_flag)
                values (?, ?, 'PG供应商', 'PG仓库', now(), '过磅', 3.00000000, 200.00, '草稿', ?, ?, true)
                """, INBOUND_ID, String.valueOf(INBOUND_ID), "PG-S-" + SUPPLIER_ID, SUPPLIER_ID);
        insertInboundItem(INBOUND_ITEM_RELEASABLE, 1, ORDER_ITEM_RELEASABLE);
        insertInboundItem(INBOUND_ITEM_RETAINED, 2, ORDER_ITEM_RETAINED);
    }

    private void seedSalesOrderReferencingRetainedItem() {
        jdbc.update("""
                insert into so_sales_order
                    (id, order_no, customer_name, project_name, delivery_date, sales_name,
                     total_weight, total_amount, status, project_id, customer_id)
                values (?, ?, 'PG客户', 'PG项目', now(), 'PG销售', 1.00000000, 100.00, '草稿', ?, ?)
                """, SALES_ORDER_ID, String.valueOf(SALES_ORDER_ID), PROJECT_ID, CUSTOMER_ID);
        jdbc.update("""
                insert into so_sales_order_item
                    (id, order_id, line_no, material_code, brand, category, material, spec, unit,
                     quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                     material_id, source_inbound_item_id)
                values (?, ?, 1, ?, 'PG品牌', 'PG类别', 'PG材质', 'PG规格', '吨',
                        1, 1.00000000, 10, 1.00000000, 100.00, 100.00, ?, ?)
                """, SALES_ORDER_ITEM, SALES_ORDER_ID, "PG-M-" + MATERIAL_ID, MATERIAL_ID, INBOUND_ITEM_RETAINED);
    }

    private void insertOrderItem(long itemId, int lineNo) {
        jdbc.update("""
                insert into po_purchase_order_item
                    (id, order_id, line_no, material_code, brand, category, material, spec, unit,
                     quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                     material_id, warehouse_id, warehouse_name)
                values (?, ?, ?, ?, 'PG品牌', 'PG类别', 'PG材质', 'PG规格', '吨',
                        1, 1.00000000, 10, 1.00000000, 100.00, 100.00, ?, ?, 'PG仓库')
                """, itemId, ORDER_ID, lineNo, "PG-M-" + MATERIAL_ID, MATERIAL_ID, WAREHOUSE_ID);
    }

    private void insertInboundItem(long itemId, int lineNo, long sourceOrderItemId) {
        jdbc.update("""
                insert into po_purchase_inbound_item
                    (id, inbound_id, line_no, material_code, brand, category, material, spec, unit,
                     quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                     source_purchase_order_item_id, settlement_mode, material_id, warehouse_id)
                values (?, ?, ?, ?, 'PG品牌', 'PG类别', 'PG材质', 'PG规格', '吨',
                        1, 1.00000000, 10, 1.00000000, 100.00, 100.00, ?, '过磅', ?, ?)
                """, itemId, INBOUND_ID, lineNo, "PG-M-" + MATERIAL_ID, sourceOrderItemId, MATERIAL_ID, WAREHOUSE_ID);
    }
}
