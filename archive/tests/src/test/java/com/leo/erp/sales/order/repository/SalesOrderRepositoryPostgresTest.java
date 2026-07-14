package com.leo.erp.sales.order.repository;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SalesOrderRepositoryPostgresTest {

    private static final long ORDER_ONE_ID = 8_890_000_000_000_000_001L;
    private static final long ORDER_TWO_ID = 8_890_000_000_000_000_002L;
    private static final long DELETED_ORDER_ID = 8_890_000_000_000_000_003L;
    private static final long ORDER_ONE_MATCHING_ITEM_ID = 8_890_000_000_000_000_101L;
    private static final long ORDER_ONE_OTHER_ITEM_ID = 8_890_000_000_000_000_102L;
    private static final long ORDER_TWO_MATCHING_ITEM_ID = 8_890_000_000_000_000_201L;
    private static final long ORDER_TWO_OTHER_ITEM_ID = 8_890_000_000_000_000_202L;
    private static final long DELETED_ORDER_ITEM_ID = 8_890_000_000_000_000_301L;
    private static final long CUSTOMER_ID = 8_890_000_000_000_000_401L;
    private static final long PROJECT_ID = 8_890_000_000_000_000_402L;
    private static final long MATERIAL_ID = 8_890_000_000_000_000_403L;
    private static final long WAREHOUSE_ID = 8_890_000_000_000_000_404L;

    @Autowired
    private SalesOrderRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertFixtures() {
        insertMasterData();
        insertOrder(ORDER_ONE_ID, "TEST-SO-REPOSITORY-1", false);
        insertOrder(ORDER_TWO_ID, "TEST-SO-REPOSITORY-2", false);
        insertOrder(DELETED_ORDER_ID, "TEST-SO-REPOSITORY-DELETED", true);
        insertOrderItem(ORDER_ONE_MATCHING_ITEM_ID, ORDER_ONE_ID, 1);
        insertOrderItem(ORDER_ONE_OTHER_ITEM_ID, ORDER_ONE_ID, 2);
        insertOrderItem(ORDER_TWO_MATCHING_ITEM_ID, ORDER_TWO_ID, 1);
        insertOrderItem(ORDER_TWO_OTHER_ITEM_ID, ORDER_TWO_ID, 2);
        insertOrderItem(DELETED_ORDER_ITEM_ID, DELETED_ORDER_ID, 1);
    }

    @Test
    void findAllWithItemsBySourceItemIds_returnsEachActiveParentWithAllItems() {
        List<SalesOrder> result = repository.findAllWithItemsBySourceItemIds(List.of(
                ORDER_ONE_MATCHING_ITEM_ID,
                ORDER_TWO_MATCHING_ITEM_ID,
                DELETED_ORDER_ITEM_ID
        ));

        assertThat(result)
                .extracting(SalesOrder::getId)
                .containsExactlyInAnyOrder(ORDER_ONE_ID, ORDER_TWO_ID);
        assertThat(result)
                .allSatisfy(order -> assertThat(order.getItems()).hasSize(2));
        SalesOrder orderOne = result.stream()
                .filter(order -> order.getId().equals(ORDER_ONE_ID))
                .findFirst()
                .orElseThrow();
        assertThat(orderOne.getItems())
                .extracting(SalesOrderItem::getId)
                .containsExactlyInAnyOrder(ORDER_ONE_MATCHING_ITEM_ID, ORDER_ONE_OTHER_ITEM_ID);
    }

    private void insertMasterData() {
        jdbcTemplate.update("""
                INSERT INTO md_customer (
                    id, customer_code, customer_name, project_name, status,
                    created_by, created_name, created_at, deleted_flag
                ) VALUES (?, 'TEST-SO-REPOSITORY-CUSTOMER', '仓储测试客户', '仓储测试项目', '正常',
                          0, 'repository-test', CURRENT_TIMESTAMP, FALSE)
                """, CUSTOMER_ID);
        jdbcTemplate.update("""
                INSERT INTO md_project (
                    id, project_code, project_name, customer_code, customer_id, status,
                    created_by, created_name, created_at, deleted_flag
                ) VALUES (?, 'TEST-SO-REPOSITORY-PROJECT', '仓储测试项目',
                          'TEST-SO-REPOSITORY-CUSTOMER', ?, '正常',
                          0, 'repository-test', CURRENT_TIMESTAMP, FALSE)
                """, PROJECT_ID, CUSTOMER_ID);
        jdbcTemplate.update("""
                INSERT INTO md_material (
                    id, material_code, brand, material, category, spec, length, unit,
                    piece_weight_ton, pieces_per_bundle, unit_price,
                    created_by, created_name, created_at, deleted_flag,
                    quantity_unit, batch_no_enabled
                ) VALUES (?, 'TEST-SO-REPOSITORY-MATERIAL', '测试品牌', '测试材质', '螺纹钢',
                          '18', '12m', '吨', 0.10000000, 1, 100.00,
                          0, 'repository-test', CURRENT_TIMESTAMP, FALSE, '件', FALSE)
                """, MATERIAL_ID);
        jdbcTemplate.update("""
                INSERT INTO md_warehouse (
                    id, warehouse_code, warehouse_name, warehouse_type, status,
                    created_by, created_name, created_at, deleted_flag
                ) VALUES (?, 'TEST-SO-REPOSITORY-WAREHOUSE', '仓储测试仓库', '自有仓', '正常',
                          0, 'repository-test', CURRENT_TIMESTAMP, FALSE)
                """, WAREHOUSE_ID);
    }

    private void insertOrder(long id, String orderNo, boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO so_sales_order (
                    id, version, order_no, customer_name, project_name, delivery_date,
                    sales_name, total_weight, total_amount, status,
                    created_by, created_name, created_at, deleted_flag,
                    customer_code, customer_id, project_id
                ) VALUES (?, 0, ?, '仓储测试客户', '仓储测试项目', CURRENT_DATE,
                          '仓储测试销售', 1.00000000, 100.00, '草稿',
                          0, 'repository-test', CURRENT_TIMESTAMP, ?,
                          'TEST-SO-REPOSITORY-CUSTOMER', ?, ?)
                """, id, orderNo, deleted, CUSTOMER_ID, PROJECT_ID);
    }

    private void insertOrderItem(long id, long orderId, int lineNo) {
        jdbcTemplate.update("""
                INSERT INTO so_sales_order_item (
                    id, order_id, line_no, material_code, brand, category, material, spec,
                    length, unit, quantity, piece_weight_ton, pieces_per_bundle,
                    weight_ton, unit_price, amount, quantity_unit, batch_no,
                    material_id, warehouse_id
                ) VALUES (?, ?, ?, 'TEST-SO-REPOSITORY-MATERIAL', '测试品牌', '螺纹钢',
                          '测试材质', '18', '12m', '吨', 1, 0.10000000, 1,
                          0.10000000, 100.00, 10.00, '件', ?, ?, ?)
                """, id, orderId, lineNo, "BATCH-" + id, MATERIAL_ID, WAREHOUSE_ID);
    }
}
