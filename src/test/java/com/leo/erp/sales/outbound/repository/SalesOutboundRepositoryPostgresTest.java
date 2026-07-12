package com.leo.erp.sales.outbound.repository;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
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
class SalesOutboundRepositoryPostgresTest {

    private static final long SOURCE_ORDER_ID = 8_891_000_000_000_000_001L;
    private static final long SOURCE_ITEM_ONE_ID = 8_891_000_000_000_000_101L;
    private static final long SOURCE_ITEM_TWO_ID = 8_891_000_000_000_000_102L;
    private static final long SOURCE_ITEM_THREE_ID = 8_891_000_000_000_000_103L;
    private static final long OUTBOUND_ONE_ID = 8_891_000_000_000_000_201L;
    private static final long OUTBOUND_TWO_ID = 8_891_000_000_000_000_202L;
    private static final long DRAFT_OUTBOUND_ID = 8_891_000_000_000_000_203L;
    private static final long DELETED_OUTBOUND_ID = 8_891_000_000_000_000_204L;
    private static final long OUTBOUND_ONE_MATCHING_ITEM_ID = 8_891_000_000_000_000_301L;
    private static final long OUTBOUND_ONE_OTHER_ITEM_ID = 8_891_000_000_000_000_302L;
    private static final long OUTBOUND_TWO_MATCHING_ITEM_ID = 8_891_000_000_000_000_303L;
    private static final long OUTBOUND_TWO_OTHER_ITEM_ID = 8_891_000_000_000_000_304L;
    private static final long DRAFT_OUTBOUND_ITEM_ID = 8_891_000_000_000_000_305L;
    private static final long DELETED_OUTBOUND_ITEM_ID = 8_891_000_000_000_000_306L;
    private static final long SOURCE_ORDER_OTHER_ITEM_ID = 8_891_000_000_000_000_107L;
    private static final long CUSTOMER_ID = 8_891_000_000_000_000_401L;
    private static final long PROJECT_ID = 8_891_000_000_000_000_402L;
    private static final long MATERIAL_ID = 8_891_000_000_000_000_403L;
    private static final long WAREHOUSE_ID = 8_891_000_000_000_000_404L;

    @Autowired
    private SalesOutboundRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertFixtures() {
        insertMasterData();
        insertSourceOrder();
        insertOutbound(OUTBOUND_ONE_ID, "TEST-SOO-REPOSITORY-1", StatusConstants.AUDITED, false);
        insertOutbound(OUTBOUND_TWO_ID, "TEST-SOO-REPOSITORY-2", StatusConstants.AUDITED, false);
        insertOutbound(DRAFT_OUTBOUND_ID, "TEST-SOO-REPOSITORY-DRAFT", StatusConstants.DRAFT, false);
        insertOutbound(DELETED_OUTBOUND_ID, "TEST-SOO-REPOSITORY-DELETED", StatusConstants.AUDITED, true);
        insertOutboundItem(OUTBOUND_ONE_MATCHING_ITEM_ID, OUTBOUND_ONE_ID, 1, SOURCE_ITEM_ONE_ID);
        insertOutboundItem(OUTBOUND_ONE_OTHER_ITEM_ID, OUTBOUND_ONE_ID, 2, SOURCE_ITEM_TWO_ID);
        insertOutboundItem(OUTBOUND_TWO_MATCHING_ITEM_ID, OUTBOUND_TWO_ID, 1, SOURCE_ITEM_TWO_ID);
        insertOutboundItem(OUTBOUND_TWO_OTHER_ITEM_ID, OUTBOUND_TWO_ID, 2, SOURCE_ITEM_THREE_ID);
        insertOutboundItem(DRAFT_OUTBOUND_ITEM_ID, DRAFT_OUTBOUND_ID, 1, SOURCE_ITEM_ONE_ID);
        insertOutboundItem(DELETED_OUTBOUND_ITEM_ID, DELETED_OUTBOUND_ID, 1, SOURCE_ITEM_ONE_ID);
    }

    @Test
    void findAllWithItemsByStatusAndSourceSalesOrderItemIds_filtersStatusAndSoftDeletedParents() {
        List<SalesOutbound> result = repository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                StatusConstants.AUDITED,
                List.of(SOURCE_ITEM_ONE_ID, SOURCE_ITEM_TWO_ID)
        );

        assertThat(result)
                .extracting(SalesOutbound::getId)
                .containsExactlyInAnyOrder(OUTBOUND_ONE_ID, OUTBOUND_TWO_ID);
        assertThat(result)
                .allSatisfy(outbound -> assertThat(outbound.getItems()).hasSize(2));

        SalesOutbound outboundOne = result.stream()
                .filter(outbound -> outbound.getId().equals(OUTBOUND_ONE_ID))
                .findFirst()
                .orElseThrow();
        assertThat(outboundOne.getItems())
                .extracting(SalesOutboundItem::getId)
                .containsExactlyInAnyOrder(OUTBOUND_ONE_MATCHING_ITEM_ID, OUTBOUND_ONE_OTHER_ITEM_ID);
    }

    private void insertMasterData() {
        jdbcTemplate.update("""
                INSERT INTO md_customer (
                    id, customer_code, customer_name, project_name, status,
                    created_by, created_name, created_at, deleted_flag
                ) VALUES (?, 'TEST-SOO-REPOSITORY-CUSTOMER', '出库仓储测试客户', '出库仓储测试项目', '正常',
                          0, 'repository-test', CURRENT_TIMESTAMP, FALSE)
                """, CUSTOMER_ID);
        jdbcTemplate.update("""
                INSERT INTO md_project (
                    id, project_code, project_name, customer_code, customer_id, status,
                    created_by, created_name, created_at, deleted_flag
                ) VALUES (?, 'TEST-SOO-REPOSITORY-PROJECT', '出库仓储测试项目',
                          'TEST-SOO-REPOSITORY-CUSTOMER', ?, '正常',
                          0, 'repository-test', CURRENT_TIMESTAMP, FALSE)
                """, PROJECT_ID, CUSTOMER_ID);
        jdbcTemplate.update("""
                INSERT INTO md_material (
                    id, material_code, brand, material, category, spec, length, unit,
                    piece_weight_ton, pieces_per_bundle, unit_price,
                    created_by, created_name, created_at, deleted_flag,
                    quantity_unit, batch_no_enabled
                ) VALUES (?, 'TEST-SOO-REPOSITORY-MATERIAL', '测试品牌', '测试材质', '螺纹钢',
                          '18', '12m', '吨', 0.10000000, 1, 100.00,
                          0, 'repository-test', CURRENT_TIMESTAMP, FALSE, '件', FALSE)
                """, MATERIAL_ID);
        jdbcTemplate.update("""
                INSERT INTO md_warehouse (
                    id, warehouse_code, warehouse_name, warehouse_type, status,
                    created_by, created_name, created_at, deleted_flag
                ) VALUES (?, 'TEST-SOO-REPOSITORY-WAREHOUSE', '出库仓储测试仓库', '自有仓', '正常',
                          0, 'repository-test', CURRENT_TIMESTAMP, FALSE)
                """, WAREHOUSE_ID);
    }

    private void insertSourceOrder() {
        jdbcTemplate.update("""
                INSERT INTO so_sales_order (
                    id, version, order_no, customer_name, project_name, delivery_date,
                    sales_name, total_weight, total_amount, status,
                    created_by, created_name, created_at, deleted_flag,
                    customer_code, customer_id, project_id
                ) VALUES (?, 0, 'TEST-SOO-REPOSITORY-SOURCE', '出库仓储测试客户', '出库仓储测试项目', CURRENT_DATE,
                          '出库仓储测试销售', 3.00000000, 300.00, '已审核',
                          0, 'repository-test', CURRENT_TIMESTAMP, FALSE,
                          'TEST-SOO-REPOSITORY-CUSTOMER', ?, ?)
                """, SOURCE_ORDER_ID, CUSTOMER_ID, PROJECT_ID);
        insertSourceOrderItem(SOURCE_ITEM_ONE_ID, 1);
        insertSourceOrderItem(SOURCE_ITEM_TWO_ID, 2);
        insertSourceOrderItem(SOURCE_ITEM_THREE_ID, 3);
        insertSourceOrderItem(SOURCE_ORDER_OTHER_ITEM_ID, 4);
    }

    private void insertSourceOrderItem(long id, int lineNo) {
        jdbcTemplate.update("""
                INSERT INTO so_sales_order_item (
                    id, order_id, line_no, material_code, brand, category, material, spec,
                    length, unit, quantity, piece_weight_ton, pieces_per_bundle,
                    weight_ton, unit_price, amount, quantity_unit, batch_no,
                    material_id, warehouse_id
                ) VALUES (?, ?, ?, 'TEST-SOO-REPOSITORY-MATERIAL', '测试品牌', '螺纹钢',
                          '测试材质', '18', '12m', '吨', 1, 0.10000000, 1,
                          0.10000000, 100.00, 10.00, '件', ?, ?, ?)
                """, id, SOURCE_ORDER_ID, lineNo, "SOURCE-BATCH-" + id, MATERIAL_ID, WAREHOUSE_ID);
    }

    private void insertOutbound(long id, String outboundNo, String status, boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO so_sales_outbound (
                    id, version, outbound_no, sales_order_no, customer_name, project_name,
                    warehouse_name, outbound_date, total_weight, total_amount, status,
                    created_by, created_name, created_at, deleted_flag,
                    customer_id, project_id, warehouse_id
                ) VALUES (?, 0, ?, 'TEST-SOO-REPOSITORY-SOURCE', '出库仓储测试客户', '出库仓储测试项目',
                          '出库仓储测试仓库', CURRENT_DATE, 1.00000000, 100.00, ?,
                          0, 'repository-test', CURRENT_TIMESTAMP, ?, ?, ?, ?)
                """, id, outboundNo, status, deleted, CUSTOMER_ID, PROJECT_ID, WAREHOUSE_ID);
    }

    private void insertOutboundItem(long id, long outboundId, int lineNo, long sourceItemId) {
        jdbcTemplate.update("""
                INSERT INTO so_sales_outbound_item (
                    id, outbound_id, line_no, material_code, brand, category, material, spec,
                    length, unit, quantity, piece_weight_ton, pieces_per_bundle,
                    weight_ton, unit_price, amount, quantity_unit, batch_no,
                    warehouse_name, source_sales_order_item_id, material_id, warehouse_id
                ) VALUES (?, ?, ?, 'TEST-SOO-REPOSITORY-MATERIAL', '测试品牌', '螺纹钢',
                          '测试材质', '18', '12m', '吨', 1, 0.10000000, 1,
                          0.10000000, 100.00, 10.00, '件', 'OUTBOUND-BATCH-' || ?,
                          '出库仓储测试仓库', ?, ?, ?)
                """, id, outboundId, lineNo, id, sourceItemId, MATERIAL_ID, WAREHOUSE_ID);
    }
}
