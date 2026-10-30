package com.leo.erp.purchase.inbound.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「已删除采购入库单残留引用」修复的真实 PostgreSQL 回归（默认跳过，设置
 * {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>锁三件事：</p>
 * <ol>
 *   <li>V203 已放开 {@code po_purchase_inbound_item.source_purchase_order_item_id} 的 NOT NULL——
 *       否则应用层解除残留引用时会撞非空约束；</li>
 *   <li>来源外键仍是 RESTRICT（活跃入库单的引用保护不能被顺手改成 CASCADE/SET NULL）；</li>
 *   <li>解除引用的 native SQL 能在真库执行（JPQL 语法/参数展开不会被 Hibernate 启动期校验，
 *       只能靠真实执行兜住）。</li>
 * </ol>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class PurchaseInboundItemSourceDetachPostgresTest {

    @Autowired
    private PurchaseInboundItemRepository purchaseInboundItemRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sourceOrderItemColumn_shouldAllowNull_afterV203() {
        String nullable = jdbc.queryForObject("""
                select is_nullable
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name = 'po_purchase_inbound_item'
                   and column_name = 'source_purchase_order_item_id'
                """, String.class);
        assertThat(nullable).isEqualTo("YES");
    }

    @Test
    void sourceOrderItemForeignKey_shouldStayRestrict() {
        // confdeltype: r = RESTRICT, c = CASCADE, n = SET NULL
        String deleteAction = jdbc.queryForObject("""
                select confdeltype::text
                  from pg_constraint
                 where conname = 'fk_po_purchase_inbound_item_source_identity'
                """, String.class);
        assertThat(deleteAction).isEqualTo("r");
    }

    @Test
    void detachSourceReferencesOfDeletedInbounds_shouldExecuteOnRealDatabase() {
        // 用不存在的来源行 ID 触发一次真实 UPDATE：本次只验证 SQL 可执行、列表参数可展开，
        // 不依赖库里是否存在残留数据（无匹配行时返回 0）。
        int affected = purchaseInboundItemRepository
                .detachSourceReferencesOfDeletedInbounds(List.of(-1L));
        assertThat(affected).isZero();
    }
}
