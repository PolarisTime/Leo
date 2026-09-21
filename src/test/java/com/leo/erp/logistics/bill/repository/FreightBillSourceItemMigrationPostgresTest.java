package com.leo.erp.logistics.bill.repository;

import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceItem;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的行级来源占用迁移回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 * <p>
 * 断言 V156 新增 {@code lg_freight_bill_source_item} 的列/唯一约束存在，订单级唯一占用索引已移除，
 * 且实体字段与迁移列一致。迁移由 Flyway 管理，本用例只读 {@code information_schema}/{@code pg_indexes}。
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

    @Test
    void v156_sourceItemTable_hasOccupancyColumns() {
        assertThat(column("lg_freight_bill_source_item", "freight_bill_id").type()).isEqualTo("bigint");
        assertThat(column("lg_freight_bill_source_item", "freight_bill_id").nullable()).isFalse();
        assertThat(column("lg_freight_bill_source_item", "source_sales_order_item_id").type()).isEqualTo("bigint");
        assertThat(column("lg_freight_bill_source_item", "source_sales_order_item_id").nullable()).isFalse();
        assertThat(column("lg_freight_bill_source_item", "quantity").type()).isEqualTo("integer");
        assertThat(column("lg_freight_bill_source_item", "quantity").nullable()).isFalse();
        ColumnMeta activeFlag = column("lg_freight_bill_source_item", "active_flag");
        assertThat(activeFlag.type()).isEqualTo("boolean");
        assertThat(activeFlag.nullable()).isFalse();
        assertThat(activeFlag.defaultValue()).contains("true");
    }

    @Test
    void v156_sourceItemTable_declaresUniqueBillSourcePair() {
        assertThat(uniqueColumns("lg_freight_bill_source_item", "uk_freight_source_item_pair"))
                .containsExactly("freight_bill_id", "source_sales_order_item_id");
    }

    @Test
    void v156_orderLevelActiveUniqueIndex_isRemoved() {
        List<String> indexNames = jdbc.queryForList("""
                select indexname from pg_indexes
                where schemaname = 'public' and tablename = 'lg_freight_bill_source_order'
                """, String.class);
        assertThat(indexNames).doesNotContain("uk_freight_source_order_active_sales");
    }

    @Test
    void v156_sourceItemEntityColumns_matchMigration() {
        List<String> entityColumns = Arrays.stream(FreightBillSourceItem.class.getDeclaredFields())
                .map(field -> field.getAnnotation(Column.class))
                .filter(annotation -> annotation != null)
                .map(Column::name)
                .toList();
        assertThat(entityColumns).contains(
                "freight_bill_id", "source_sales_order_item_id", "quantity", "active_flag");
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

    private List<String> uniqueColumns(String table, String constraintName) {
        return jdbc.queryForList("""
                select kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema
                where tc.table_schema = 'public' and tc.table_name = ?
                  and tc.constraint_type = 'UNIQUE' and tc.constraint_name = ?
                order by kcu.ordinal_position
                """, String.class, table, constraintName);
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
