package com.leo.erp.common.support;

import com.leo.erp.common.charge.api.DocumentChargeItemRequest;
import com.leo.erp.common.charge.api.DocumentChargeItemResponse;
import com.leo.erp.common.charge.domain.entity.DocumentChargeItem;
import com.leo.erp.common.charge.repository.DocumentChargeItemRepository;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "拖动明细行调整顺序"的真实库回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>覆盖三件事:</p>
 * <ol>
 *   <li>明细按 {@code line_no} 回读: 插入时故意让<b>堆顺序、主键顺序都与行号顺序不同</b>,
 *       重新加载后集合仍必须是 1..N —— 这正是拖动后 GET 回来"顺序没变"的根因(缺 @OrderBy);</li>
 *   <li>费用行换位保存: 未删除行上有 {@code (module_key, document_id, line_no)} 唯一偏索引,
 *       真实索引下换位必须保存成功且回读等于提交顺序(两阶段重编号);</li>
 *   <li>所有涉及表的 {@code line_no} 存在、NOT NULL, 且历史行没有空/非正行号
 *       (本轮没有加列迁移, 因此用真实数据断言代替"迁移默认值"断言)。</li>
 * </ol>
 *
 * <p>单据头/明细行用 JDBC 直插: JPA 会把未赋值的映射列显式写成 NULL, 从而绕过库里的
 * DEFAULT(如 {@code so_sales_order.order_date}); 直插只给必填列, 其余交给默认值。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({DocumentChargeItemService.class, DetailRowOrderPersistencePostgresTest.StubConfig.class})
class DetailRowOrderPersistencePostgresTest {

    private static final long SALES_ORDER_ID = 991000000000000001L;
    private static final long PURCHASE_ORDER_ID = 991000000000000002L;
    private static final long CHARGE_DOCUMENT_ID = 991000000000000003L;
    private static final long CHARGE_ITEM_A_ID = 991000000000000011L;
    private static final long CHARGE_ITEM_B_ID = 991000000000000012L;

    /** 全部需要"拖动即持久化"的明细表(与 {@link DetailRowOrderContractTest} 一一对应)。 */
    private static final List<String> DETAIL_TABLES = List.of(
            "so_sales_order_item",
            "so_sales_outbound_item",
            "so_sales_return_item",
            "po_purchase_order_item",
            "po_purchase_inbound_item",
            "lg_freight_bill_item",
            "st_customer_statement_item",
            "st_freight_statement_item",
            "fm_payment_allocation",
            "fm_receipt_allocation",
            "bd_document_charge_item",
            "mk_quote_item");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DocumentChargeItemService documentChargeItemService;

    @Autowired
    private DocumentChargeItemRepository documentChargeItemRepository;

    @Test
    void salesOrderDetailRows_readBackInLineNoOrder() {
        Map<String, Object> project = firstRow(
                "select id, customer_id from md_project where customer_id is not null order by id limit 1");
        Assumptions.assumeFalse(project == null, "开发库缺少 md_project(含 customer_id)基础数据");
        Long projectId = number(project.get("id"));
        Long customerId = number(project.get("customer_id"));
        Long materialId = firstId("select id from md_material order by id limit 1");
        Assumptions.assumeFalse(materialId == null, "开发库缺少 md_material 基础数据");
        // chk_so_sales_order_item_source_required_v57: 销售订单明细必须且只能有一个来源
        Long sourceItemId = firstId("select id from po_purchase_order_item order by id limit 1");
        Assumptions.assumeFalse(sourceItemId == null, "开发库缺少 po_purchase_order_item 来源数据");

        jdbc.update("""
                insert into so_sales_order (id, order_no, customer_id, customer_name, project_id, project_name,
                                            delivery_date, sales_name, total_weight, total_amount, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, SALES_ORDER_ID, "PG-ORDER-" + SALES_ORDER_ID, customerId, "客户A", projectId, "项目A",
                LocalDateTime.now(), "业务员A", new BigDecimal("30"), new BigDecimal("9000"), "草稿");
        // 物理插入顺序与主键顺序都故意与行号相反: 行号 3/1/2 ← id 1001/1002/1003
        insertSalesItem(1001L, 3, materialId, sourceItemId);
        insertSalesItem(1002L, 1, materialId, sourceItemId);
        insertSalesItem(1003L, 2, materialId, sourceItemId);
        entityManager.clear();

        SalesOrder reloaded = entityManager.find(SalesOrder.class, SALES_ORDER_ID);

        assertThat(reloaded.getItems())
                .as("应按 line_no 回读, 而不是数据库堆顺序/主键顺序")
                .extracting(SalesOrderItem::getLineNo)
                .containsExactly(1, 2, 3);
        assertThat(reloaded.getItems())
                .extracting(SalesOrderItem::getId)
                .containsExactly(1002L, 1003L, 1001L);
    }

    @Test
    void purchaseOrderDetailRows_readBackInLineNoOrder() {
        Long supplierId = firstId("select id from md_supplier order by id limit 1");
        Long materialId = firstId("select id from md_material order by id limit 1");
        Assumptions.assumeFalse(supplierId == null || materialId == null,
                "开发库缺少 md_supplier/md_material 基础数据");

        jdbc.update("""
                insert into po_purchase_order (id, order_no, supplier_id, supplier_code, supplier_name,
                                               order_date, total_weight, total_amount, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, PURCHASE_ORDER_ID, "PG-PO-" + PURCHASE_ORDER_ID, supplierId, "SUP-" + supplierId, "沙钢",
                LocalDateTime.now(), new BigDecimal("30"), new BigDecimal("9000"), "草稿");
        insertPurchaseItem(2001L, 3, materialId);
        insertPurchaseItem(2002L, 1, materialId);
        insertPurchaseItem(2003L, 2, materialId);
        entityManager.clear();

        PurchaseOrder reloaded = entityManager.find(PurchaseOrder.class, PURCHASE_ORDER_ID);

        assertThat(reloaded.getItems())
                .extracting(PurchaseOrderItem::getLineNo)
                .containsExactly(1, 2, 3);
        assertThat(reloaded.getItems())
                .extracting(PurchaseOrderItem::getId)
                .containsExactly(2002L, 2003L, 2001L);
    }

    /**
     * 费用行换位: 真实唯一偏索引下 1↔2 互换必须保存成功, 且回读等于提交顺序。
     * <p>修复前逐行改号会在同一 flush 撞 {@code uk_bd_document_charge_item_document_line_active},
     * 保存直接 500。</p>
     */
    @Test
    void chargeItems_reorder_persistsSubmittedOrderUnderUniqueIndex() {
        documentChargeItemRepository.save(chargeItem(CHARGE_ITEM_A_ID, 1, "费用A"));
        documentChargeItemRepository.save(chargeItem(CHARGE_ITEM_B_ID, 2, "费用B"));
        documentChargeItemRepository.flush();

        // 拖拽后提交顺序: B 在前
        documentChargeItemService.sync(ModuleKeys.SALES_ORDER, CHARGE_DOCUMENT_ID, List.of(
                new DocumentChargeItemRequest(CHARGE_ITEM_B_ID, "费用B", null, new BigDecimal("20.00"), null, null),
                new DocumentChargeItemRequest(CHARGE_ITEM_A_ID, "费用A", null, new BigDecimal("10.00"), null, null)));
        entityManager.flush();
        entityManager.clear();

        List<DocumentChargeItemResponse> reloaded =
                documentChargeItemService.list(ModuleKeys.SALES_ORDER, CHARGE_DOCUMENT_ID);

        assertThat(reloaded)
                .extracting(DocumentChargeItemResponse::id)
                .containsExactly(CHARGE_ITEM_B_ID, CHARGE_ITEM_A_ID);
        assertThat(reloaded)
                .extracting(DocumentChargeItemResponse::lineNo)
                .containsExactly(1, 2);

        // 再拖回来(1↔2 反向): 同样必须成功
        documentChargeItemService.sync(ModuleKeys.SALES_ORDER, CHARGE_DOCUMENT_ID, List.of(
                new DocumentChargeItemRequest(CHARGE_ITEM_A_ID, "费用A", null, new BigDecimal("10.00"), null, null),
                new DocumentChargeItemRequest(CHARGE_ITEM_B_ID, "费用B", null, new BigDecimal("20.00"), null, null)));
        entityManager.flush();
        entityManager.clear();

        assertThat(documentChargeItemService.list(ModuleKeys.SALES_ORDER, CHARGE_DOCUMENT_ID))
                .extracting(DocumentChargeItemResponse::id)
                .containsExactly(CHARGE_ITEM_A_ID, CHARGE_ITEM_B_ID);
    }

    /** 排序列存在性 + 历史行行号有效性(没有加列迁移, 故以真实数据替代"迁移默认值"断言)。 */
    @Test
    void detailTables_lineNoIsNotNullAndHistoricRowsArePositive() {
        for (String table : DETAIL_TABLES) {
            String nullable = jdbc.queryForObject("""
                    select is_nullable
                      from information_schema.columns
                     where table_schema = 'public'
                       and table_name = ?
                       and column_name = 'line_no'
                    """, String.class, table);
            assertThat(nullable)
                    .as("%s 必须有 line_no 列且为 NOT NULL", table)
                    .isEqualTo("NO");

            Integer invalid = jdbc.queryForObject(
                    "select count(*) from public." + table + " where line_no is null or line_no < 1",
                    Integer.class);
            assertThat(invalid)
                    .as("%s 存在没有有效行号的历史行(line_no 为空或 < 1), 需要新增迁移回填", table)
                    .isZero();
        }
    }

    private void insertSalesItem(long id, int lineNo, long materialId, long sourceItemId) {
        jdbc.update("""
                insert into so_sales_order_item (id, order_id, line_no, material_id, material_code, brand,
                                                 category, material, spec, length, unit, quantity,
                                                 piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                                                 source_purchase_order_item_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, SALES_ORDER_ID, lineNo, materialId, "M-" + lineNo, "中天", "螺纹钢", "HRB400E",
                "12", "9米", "吨", 10, new BigDecimal("1"), 10, new BigDecimal("10"),
                new BigDecimal("3000"), new BigDecimal("30000"), sourceItemId);
    }

    private void insertPurchaseItem(long id, int lineNo, long materialId) {
        jdbc.update("""
                insert into po_purchase_order_item (id, order_id, line_no, material_id, material_code, brand,
                                                    category, material, spec, length, unit, quantity,
                                                    piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, PURCHASE_ORDER_ID, lineNo, materialId, "M-" + lineNo, "中天", "螺纹钢", "HRB400E",
                "12", "9米", "吨", 10, new BigDecimal("1"), 10, new BigDecimal("10"),
                new BigDecimal("3000"), new BigDecimal("30000"));
    }

    private Map<String, Object> firstRow(String sql) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long firstId(String sql) {
        List<Long> ids = jdbc.queryForList(sql, Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static DocumentChargeItem chargeItem(long id, int lineNo, String name) {
        DocumentChargeItem item = new DocumentChargeItem();
        item.setId(id);
        item.setModuleKey(ModuleKeys.SALES_ORDER);
        item.setDocumentId(CHARGE_DOCUMENT_ID);
        item.setLineNo(lineNo);
        item.setChargeName(name);
        item.setAmount(new BigDecimal("10.00"));
        // @DataJpaTest 下审计字段不会自动填充(无 JPA auditing 上下文), 显式补齐 NOT NULL 的 created_*
        item.setCreatedBy(0L);
        item.setCreatedName("system");
        item.setCreatedAt(LocalDateTime.now());
        return item;
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        SnowflakeIdGenerator snowflakeIdGenerator() {
            return new SnowflakeIdGenerator();
        }
    }
}
