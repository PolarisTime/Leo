package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.logistics.bill.repository.FreightBillItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.outbound.repository.SalesOutboundItemRepository;
import com.leo.erp.sales.returns.repository.SalesReturnItemRepository;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「软删除下游单据不再锁定上游明细」的真库回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>锁两件事：</p>
 * <ol>
 *   <li>V204 的 schema 约定：6 个来源列可空、「销售订单明细来源必填」CHECK 已删除、
 *       互斥 CHECK 仍在（必填口径下移到 {@code SalesOrderSourceAllocationService}）；</li>
 *   <li>7 个删除入口的解绑 native SQL 能在真库执行（native 查询不会被 Hibernate 启动期校验，
 *       只能靠真实执行兜住语法/表名/列名错误）。</li>
 * </ol>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SoftDeletedItemSourceDetachPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PurchaseInboundItemRepository purchaseInboundItemRepository;

    @Autowired
    private SalesOrderItemRepository salesOrderItemRepository;

    @Autowired
    private SalesOutboundItemRepository salesOutboundItemRepository;

    @Autowired
    private SalesReturnItemRepository salesReturnItemRepository;

    @Autowired
    private CustomerStatementRepository customerStatementRepository;

    @Autowired
    private FreightStatementRepository freightStatementRepository;

    @Autowired
    private FreightBillItemRepository freightBillItemRepository;

    @Test
    void v204_shouldRelaxSourceColumnsAndDropRequiredCheck() {
        List<String> nullableColumns = jdbc.queryForList("""
                select table_name || '.' || column_name
                  from information_schema.columns
                 where table_schema = 'public'
                   and is_nullable = 'YES'
                   and (
                        (table_name = 'so_sales_outbound_item' and column_name = 'source_sales_order_item_id')
                     or (table_name = 'so_sales_return_item' and column_name = 'source_sales_outbound_item_id')
                     or (table_name = 'st_customer_statement_item' and column_name = 'source_sales_order_item_id')
                     or (table_name = 'st_freight_statement_item' and column_name = 'source_freight_bill_item_id')
                     or (table_name = 'po_purchase_refund_item' and column_name = 'source_purchase_order_item_id')
                     or (table_name = 'st_supplier_statement_item' and column_name = 'source_inbound_item_id')
                   )
                """, String.class);
        assertThat(nullableColumns).hasSize(6);

        Integer requiredCheck = jdbc.queryForObject("""
                select count(*) from pg_constraint
                 where conrelid = 'so_sales_order_item'::regclass
                   and conname = 'chk_so_sales_order_item_source_required_v57'
                """, Integer.class);
        assertThat(requiredCheck).isZero();

        Integer exclusiveCheck = jdbc.queryForObject("""
                select count(*) from pg_constraint
                 where conrelid = 'so_sales_order_item'::regclass
                   and conname = 'chk_so_sales_order_item_source_exclusive'
                """, Integer.class);
        assertThat(exclusiveCheck).isEqualTo(1);
    }

    @Test
    void allDetachStatements_shouldExecuteOnRealDatabase() {
        // 用不存在的头 ID 触发真实 UPDATE：只验证 SQL 可执行（0 行不报错）。
        assertThat(purchaseInboundItemRepository.detachSourceReferencesOfDeletedInbound(-1L)).isZero();
        assertThat(salesOrderItemRepository.detachSourceReferencesOfDeletedOrder(-1L)).isZero();
        assertThat(salesOutboundItemRepository.detachSourceReferencesOfDeletedOutbound(-1L)).isZero();
        assertThat(salesReturnItemRepository.detachSourceReferencesOfDeletedReturn(-1L)).isZero();
        assertThat(customerStatementRepository.detachSourceReferencesOfDeletedStatement(-1L)).isZero();
        assertThat(freightStatementRepository.detachSourceReferencesOfDeletedStatement(-1L)).isZero();
        assertThat(freightBillItemRepository.detachSourceReferencesOfDeletedBill(-1L)).isZero();
    }
}
