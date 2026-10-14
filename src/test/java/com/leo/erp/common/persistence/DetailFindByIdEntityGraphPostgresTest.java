package com.leo.erp.common.persistence;

import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 详情查询抓取图回归（真实 PostgreSQL，默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>销售订单、采购订单、采购入库、物流单、收付款单的详情都走 {@code repository.findById(id)}：该方法
 * 属于详情可见性入口（详情允许查看已删除记录），所以修复方式是给 {@code findById} 单独覆写
 * {@code @EntityGraph}，而不是改成 {@code findByIdAndDeletedFlagFalse}（后者会额外过滤
 * {@code deleted_flag}，属可见性语义变更）。</p>
 *
 * <p>断言用"生成的 SQL"作为观察点：用不存在的 id 触发一次 {@code findById}，正确实现会对子表做
 * join（一次查询带出明细），错误实现只有父表一条 select。子表名与 {@code deleted_flag} 过滤同样可从
 * SQL 观察，因此本用例不依赖开发库里的既有业务数据（只额外用一次现有销售订单行验证集合真的被初始化）。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.leo.erp.common.persistence.SqlCaptureInspector"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class DetailFindByIdEntityGraphPostgresTest {

    /** 开发库里不会存在的 id，用于触发一次真实的 findById 查询而无需夹具。 */
    private static final long ABSENT_ID = 900000000000000099L;

    private static final long CARRIER_ID = 900000000000000201L;
    private static final long FREIGHT_BILL_ID = 900000000000000202L;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseInboundRepository purchaseInboundRepository;

    @Autowired
    private FreightBillRepository freightBillRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void salesOrderDetailFetchesItemsInSingleQuery() {
        assertDetailFetchGraph(captureSql(() -> salesOrderRepository.findById(ABSENT_ID)), "so_sales_order_item");
    }

    @Test
    void purchaseOrderDetailFetchesItemsInSingleQuery() {
        assertDetailFetchGraph(
                captureSql(() -> purchaseOrderRepository.findById(ABSENT_ID)),
                "po_purchase_order_item"
        );
    }

    @Test
    void purchaseInboundDetailFetchesItemsInSingleQuery() {
        assertDetailFetchGraph(
                captureSql(() -> purchaseInboundRepository.findById(ABSENT_ID)),
                "po_purchase_inbound_item"
        );
    }

    @Test
    void freightBillDetailFetchesSourceOrdersInSingleQuery() {
        // 物流单详情挂了两个集合(items + sourceOrders)：Hibernate 一次查询只会把其中一个 join 进主语句，
        // 另一个集合由同一次抓取图的第二条 select 装载，所以这里只断言主语句里出现了集合 join；
        // items 是否真的被初始化改由下面的真实行用例断言。
        String sql = captureSql(() -> freightBillRepository.findById(ABSENT_ID));
        assertThat(sql)
                .as("物流单详情 findById 必须带上抓取图(join sourceOrders)，实际 SQL: %s", sql)
                .contains("lg_freight_bill_source_order");
        assertThat(sql)
                .as("详情允许查看已删除记录，findById 不得引入 deleted_flag 过滤，实际 SQL: %s", sql)
                .doesNotContainPattern("(?i)deleted_flag\\s*=");
    }

    @Test
    void paymentDetailFetchesItemsInSingleQuery() {
        assertDetailFetchGraph(captureSql(() -> paymentRepository.findById(ABSENT_ID)), "fm_payment_allocation");
    }

    @Test
    void receiptDetailFetchesItemsInSingleQuery() {
        assertDetailFetchGraph(captureSql(() -> receiptRepository.findById(ABSENT_ID)), "fm_receipt_allocation");
    }

    /**
     * SQL 只能证明"发起了 join"，这里再用一条现有销售订单证明明细集合确实已初始化（无需访问集合本身，
     * 避免测试自己触发懒加载把断言弄成恒真）。
     */
    @Test
    void salesOrderDetailKeepsItemsInitializedForExistingRow() {
        Long existingId = jdbc.query(
                "select id from so_sales_order order by id limit 1",
                rs -> rs.next() ? rs.getLong("id") : null
        );
        assumeTrue(existingId != null, "开发库没有销售订单数据，跳过已初始化断言");

        SalesOrder order = salesOrderRepository.findById(existingId).orElseThrow();
        assertThat(Hibernate.isInitialized(order.getItems()))
                .as("详情 findById 必须一次带出 items，不能留成未初始化的懒集合")
                .isTrue();
    }

    /**
     * 物流单详情挂了两个集合，单条 SQL 只能观察到一个 join，所以这里自建一行夹具，
     * 直接断言两个集合都已初始化（夹具在 @DataJpaTest 事务里，测试结束自动回滚）。
     */
    @Test
    void freightBillDetailInitializesItemsAndSourceOrdersForExistingRow() {
        jdbc.update("""
                insert into md_carrier (id, carrier_code, carrier_name, status, deleted_flag,
                                        created_by, created_name, created_at, version)
                values (?, 'PG-L2-GRAPH-C', 'L2抓取图承运商', '正常', false, 0, 'system', now(), 0)
                """, CARRIER_ID);
        jdbc.update("""
                insert into lg_freight_bill (id, bill_no, carrier_name, bill_time, unit_price, total_weight,
                                             total_freight, status, carrier_code, carrier_id, version, created_by,
                                             created_name, created_at, deleted_flag)
                values (?, 'FB-L2-GRAPH', 'L2抓取图承运商', now(), 100.00, 12.50000000, 1250.00, '草稿',
                        'PG-L2-GRAPH-C', ?, 0, 0, 'system', now(), false)
                """, FREIGHT_BILL_ID, CARRIER_ID);

        FreightBill bill = freightBillRepository.findById(FREIGHT_BILL_ID).orElseThrow();

        assertThat(Hibernate.isInitialized(bill.getItems()))
                .as("物流单详情必须一次带出 items")
                .isTrue();
        assertThat(Hibernate.isInitialized(bill.getSourceOrders()))
                .as("物流单详情必须一次带出 sourceOrders")
                .isTrue();
    }

    private String captureSql(Runnable query) {
        SqlCaptureInspector.reset();
        query.run();
        return SqlCaptureInspector.capturedSql().toLowerCase(Locale.ROOT);
    }

    private void assertDetailFetchGraph(String sql, String childTable) {
        assertThat(sql)
                .as("详情 findById 必须与明细同一次查询(join 子表)，实际 SQL: %s", sql)
                .contains(childTable);
        assertThat(sql)
                .as("详情允许查看已删除记录，findById 不得引入 deleted_flag 过滤，实际 SQL: %s", sql)
                .doesNotContainPattern("(?i)deleted_flag\\s*=");
    }
}
