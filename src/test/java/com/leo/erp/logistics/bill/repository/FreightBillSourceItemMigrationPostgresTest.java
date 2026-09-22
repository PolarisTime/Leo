package com.leo.erp.logistics.bill.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的行级占用迁移回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 * <p>
 * 断言 V157 已删除冗余占用表 {@code lg_freight_bill_source_item}，V156 移除的订单级唯一占用索引
 * {@code uk_freight_source_order_active_sales} 仍不存在，且数量真源列
 * {@code lg_freight_bill_item.source_sales_order_item_id} 存在。迁移由 Flyway 管理，
 * 本用例只读 {@code information_schema}/{@code pg_indexes}。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class FreightBillSourceItemMigrationPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FreightBillItemRepository freightBillItemRepository;

    @Test
    void v157_sourceItemTable_isDropped() {
        assertThat(toRegclass("lg_freight_bill_source_item")).isNull();
    }

    @Test
    void v157_occupancyAggregateQuery_isRunnableOnMigratedSchema() {
        // 空库 V1→V157 迁移后，明细占用聚合查询可直接执行（无匹配来源行返回空）。
        List<FreightBillItemRepository.FreightBillItemOccupancySummary> result =
                freightBillItemRepository.summarizeOccupiedQuantities(List.of(-1L), null);
        assertThat(result).isEmpty();
    }

    @Test
    void v156_orderLevelActiveUniqueIndex_isStillRemoved() {
        List<String> indexNames = jdbc.queryForList("""
                select indexname from pg_indexes
                where schemaname = 'public' and tablename = 'lg_freight_bill_source_order'
                """, String.class);
        assertThat(indexNames).doesNotContain("uk_freight_source_order_active_sales");
    }

    @Test
    void freightBillItem_keepsSourceSalesOrderItemIdColumn() {
        assertThat(column("lg_freight_bill_item", "source_sales_order_item_id").type()).isEqualTo("bigint");
        assertThat(column("lg_freight_bill_item", "quantity").type()).isEqualTo("integer");
        assertThat(column("lg_freight_bill_item", "quantity").nullable()).isFalse();
    }

    private String toRegclass(String table) {
        return jdbc.queryForObject("select to_regclass(?)::text", String.class, "public." + table);
    }

    private ColumnMeta column(String table, String column) {
        return jdbc.queryForObject("""
                select data_type, is_nullable, column_default, character_maximum_length,
                       numeric_precision, numeric_scale
                from information_schema.columns
                where table_schema = 'public' and table_name = ? and column_name = ?
                """,
                (rs, rowNum) -> new ColumnMeta(
                        rs.getString("data_type"),
                        "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                        rs.getString("column_default"),
                        rs.getObject("character_maximum_length", Integer.class),
                        rs.getObject("numeric_precision", Integer.class),
                        rs.getObject("numeric_scale", Integer.class)),
                table, column);
    }

    private record ColumnMeta(String type, boolean nullable, String defaultValue,
                              Integer maxLength, Integer numericPrecision, Integer numericScale) {
        ColumnMeta {
            if (type != null) {
                type = type.toLowerCase(Locale.ROOT);
            }
        }
    }
}
