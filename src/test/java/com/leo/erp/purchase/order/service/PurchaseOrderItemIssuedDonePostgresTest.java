package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemIssuedDoneResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 采购订单明细行「已开完」手动标记的真库回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>覆盖 V174 结构(varchar/timestamp 类型、NOT NULL 默认 false)与真实落库留痕:
 * 标记写谁/何时、幂等不覆盖首次留痕、取消清空、不存在 404。</p>
 *
 * <p>用例在 {@code @DataJpaTest} 事务内执行并自动回滚, 不留垃圾数据。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PurchaseOrderItemIssuedDoneService.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class PurchaseOrderItemIssuedDonePostgresTest {

    private static final long ORDER_ID = 945000000000000101L;
    private static final long ITEM_ID = 945000000000000102L;

    @Autowired
    private PurchaseOrderItemIssuedDoneService service;

    @Autowired
    private JdbcTemplate jdbc;

    /** V174 结构断言: issued_done NOT NULL 默认 false, 留痕三列可空(取消后为 null)。 */
    @Test
    void migration_addsIssuedDoneColumnsWithExpectedNullability() {
        Map<String, String[]> columns = jdbc.query("""
                select column_name, is_nullable, column_default, data_type from information_schema.columns
                where table_schema = 'public' and table_name = 'po_purchase_order_item'
                  and column_name like 'issued_done%'
                """, rs -> {
            Map<String, String[]> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("column_name"), new String[] {rs.getString("is_nullable"),
                        String.valueOf(rs.getString("column_default")), rs.getString("data_type")});
            }
            return result;
        });

        assertThat(columns).containsKeys("issued_done", "issued_done_by", "issued_done_name", "issued_done_at");
        assertThat(columns.get("issued_done")[0]).isEqualTo("NO");
        assertThat(columns.get("issued_done")[1]).contains("false");
        assertThat(columns.get("issued_done")[2]).isEqualTo("boolean");
        assertThat(columns.get("issued_done_by")[0]).isEqualTo("YES");
        assertThat(columns.get("issued_done_name")[0]).isEqualTo("YES");
        assertThat(columns.get("issued_done_at")[0]).isEqualTo("YES");
    }

    /** 标记已开完: 真库写入 true 与留痕(谁/何时), 回读一致。 */
    @Test
    void mark_persistsFlagAndTraceOnRealSchema() {
        insertOrderAndItem();

        PurchaseOrderItemIssuedDoneResponse response = service.mark(ITEM_ID, true, 42L, "张三");

        assertThat(response.issuedDone()).isTrue();
        assertThat(response.issuedDoneBy()).isEqualTo(42L);
        assertThat(response.issuedDoneName()).isEqualTo("张三");
        assertThat(response.issuedDoneAt()).isNotNull();

        Map<String, Object> row = jdbc.queryForMap("""
                select issued_done, issued_done_by, issued_done_name, issued_done_at
                from public.po_purchase_order_item where id = ?
                """, ITEM_ID);
        assertThat(row.get("issued_done")).isEqualTo(true);
        assertThat(((Number) row.get("issued_done_by")).longValue()).isEqualTo(42L);
        assertThat(row.get("issued_done_name")).isEqualTo("张三");
        assertThat(row.get("issued_done_at")).isNotNull();
    }

    /** 幂等: 重复标记不覆盖首次留痕(谁先标就是谁)。 */
    @Test
    void mark_isIdempotentAndKeepsFirstTraceOnRealSchema() {
        insertOrderAndItem();

        LocalDateTime firstAt = service.mark(ITEM_ID, true, 42L, "张三").issuedDoneAt();
        PurchaseOrderItemIssuedDoneResponse second = service.mark(ITEM_ID, true, 99L, "李四");

        assertThat(second.issuedDoneBy()).isEqualTo(42L);
        assertThat(second.issuedDoneName()).isEqualTo("张三");
        assertThat(second.issuedDoneAt()).isEqualTo(firstAt);
        assertThat(jdbc.queryForObject("""
                select issued_done_name from public.po_purchase_order_item where id = ?
                """, String.class, ITEM_ID)).isEqualTo("张三");
    }

    /** 取消标记: 状态与留痕一并清空(真库列回到 false/null)。 */
    @Test
    void clear_resetsFlagAndTraceOnRealSchema() {
        insertOrderAndItem();
        service.mark(ITEM_ID, true, 42L, "张三");

        PurchaseOrderItemIssuedDoneResponse cleared = service.mark(ITEM_ID, false, 42L, "张三");

        assertThat(cleared.issuedDone()).isFalse();
        assertThat(cleared.issuedDoneAt()).isNull();
        Map<String, Object> row = jdbc.queryForMap("""
                select issued_done, issued_done_by, issued_done_name, issued_done_at
                from public.po_purchase_order_item where id = ?
                """, ITEM_ID);
        assertThat(row.get("issued_done")).isEqualTo(false);
        assertThat(row.get("issued_done_by")).isNull();
        assertThat(row.get("issued_done_name")).isNull();
        assertThat(row.get("issued_done_at")).isNull();
    }

    /** 明细行不存在 → 404(不写库)。 */
    @Test
    void mark_missingItemReturnsNotFoundOnRealSchema() {
        insertOrderAndItem();

        assertThatThrownBy(() -> service.mark(999_999_999L, true, 42L, "张三"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 造最小供应商 + 采购订单 + 明细行(迁移里 order.supplier_id 与 item.order_id 都有 FK)。 */
    private void insertOrderAndItem() {
        jdbc.update("""
                insert into public.md_supplier (id, supplier_code, supplier_name, status,
                                                created_by, created_name, deleted_flag)
                values (945000000000000103, 'PG-ISSUED-DONE-GYS', 'PG已开完供应商', '正常', 0, 'pg-test', false)
                """);
        jdbc.update("""
                insert into public.md_material (id, material_code, brand, material, category, spec, length,
                                                unit, piece_weight_ton, pieces_per_bundle, unit_price,
                                                created_by, created_name, deleted_flag)
                values (945000000000000104, ?, 'PG已开完品牌', 'PG已开完材质', '螺纹钢', '12', '9米', '吨',
                        2.0, 100, 3000, 0, 'pg-test', false)
                """, "PG-ISSUED-DONE-" + ITEM_ID);
        jdbc.update("""
                insert into public.po_purchase_order (id, order_no, supplier_code, supplier_id, supplier_name,
                                                      order_date, total_weight, total_amount, status,
                                                      created_by, created_name, deleted_flag)
                values (?, ?, 'PG-ISSUED-DONE-GYS', 945000000000000103, 'PG已开完供应商',
                        current_timestamp, 10.00000000, 30000.00, '已审核', 0, 'pg-test', false)
                """, ORDER_ID, "PG-ISSUED-DONE-" + ORDER_ID);
        jdbc.update("""
                insert into public.po_purchase_order_item (id, order_id, line_no, material_id, material_code,
                                                           brand, category, material, spec, length, unit,
                                                           quantity, piece_weight_ton, pieces_per_bundle,
                                                           weight_ton, unit_price, amount)
                values (?, ?, 1, 945000000000000104, ?, 'PG已开完品牌', '螺纹钢', 'PG已开完材质', '12', '9米', '吨', 10,
                        2.00000000, 100, 10.00000000, 3000.00, 30000.00)
                """, ITEM_ID, ORDER_ID, "PG-ISSUED-DONE-" + ITEM_ID);
    }
}
