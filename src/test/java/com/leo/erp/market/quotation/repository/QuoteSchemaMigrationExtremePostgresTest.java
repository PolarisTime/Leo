package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.enums.QuoteRowType;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的迁移/映射一致性回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>
 * 断言 V142–V146 的列/类型/NOT NULL/默认值/唯一约束与当前实体一致: 新增列存在且可空性正确,
 * V146 删除的 {@code quantity_mode}/{@code pieces}/{@code piece_weight_ton} 已不存在且实体不再引用。
 * 迁移脚本由 Flyway 管理, 本用例只读 {@code information_schema}, 不会改写 schema。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class QuoteSchemaMigrationExtremePostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v142_quoteItemPrice_hasSupplierSnapshotColumns() {
        assertThat(column("mk_quote_item_price", "supplier_id").type()).isEqualTo("bigint");
        assertThat(column("mk_quote_item_price", "supplier_name").type())
                .isEqualTo("character varying");
        assertThat(column("mk_quote_item_price", "supplier_name").maxLength()).isEqualTo(200);
        assertThat(column("mk_quote_item_price", "supplier_id").nullable()).isTrue();
        assertThat(column("mk_quote_item_price", "supplier_name").nullable()).isTrue();
    }

    @Test
    void v142_quoteProjectConfig_declaresNotNullDefaultsAndUniqueProject() {
        ColumnMeta version = column("mk_quote_project_config", "version");
        assertThat(version.type()).isEqualTo("bigint");
        assertThat(version.nullable()).isFalse();
        assertThat(version.defaultValue()).contains("0");

        ColumnMeta lengthPremium = column("mk_quote_project_config", "length_premium");
        assertThat(lengthPremium.type()).isEqualTo("numeric");
        assertThat(lengthPremium.numericPrecision()).isEqualTo(10);
        assertThat(lengthPremium.numericScale()).isEqualTo(2);
        assertThat(lengthPremium.nullable()).isFalse();
        assertThat(lengthPremium.defaultValue()).contains("30");

        ColumnMeta fallback = column("mk_quote_project_config", "hrb400e_fallback");
        assertThat(fallback.type()).isEqualTo("boolean");
        assertThat(fallback.nullable()).isFalse();
        assertThat(fallback.defaultValue()).contains("false");

        assertThat(uniqueColumns("mk_quote_project_config", "uk_quote_project_config_project"))
                .containsExactly("project_id");
        assertThat(uniqueColumns("mk_quote_project_brand", "uk_quote_project_brand"))
                .containsExactly("config_id", "brand_name");
    }

    @Test
    void v145_quoteSheet_declaresIndependentSpecQuantityLockColumn() {
        ColumnMeta locked = column("mk_quote_sheet", "spec_quantity_locked");
        assertThat(locked.type()).isEqualTo("boolean");
        assertThat(locked.nullable()).isFalse();
        assertThat(locked.defaultValue()).contains("false");
        assertThat(new QuoteSheet().isSpecQuantityLocked()).isFalse();
    }

    @Test
    void v146_droppedQuantityModeColumnsAreAbsent_andEntityDoesNotReferenceThem() {
        assertThat(columnNames("mk_quote_item"))
                .doesNotContain("quantity_mode", "pieces", "piece_weight_ton");

        List<String> entityColumns = Arrays.stream(QuoteSheetItem.class.getDeclaredFields())
                .map(field -> field.getAnnotation(Column.class))
                .filter(annotation -> annotation != null)
                .map(Column::name)
                .toList();
        assertThat(entityColumns).doesNotContain("quantity_mode", "pieces", "piece_weight_ton");
    }

    @Test
    void v154_quoteItem_rowTypeNotNullDefault_andProductColumnsNullable() {
        ColumnMeta rowType = column("mk_quote_item", "row_type");
        assertThat(rowType.type()).isEqualTo("character varying");
        assertThat(rowType.maxLength()).isEqualTo(16);
        assertThat(rowType.nullable()).isFalse();
        assertThat(rowType.defaultValue()).contains("PRODUCT");

        // 隔断行不携带商品字段: 这四列放开 NOT NULL
        assertThat(column("mk_quote_item", "category").nullable()).isTrue();
        assertThat(column("mk_quote_item", "material").nullable()).isTrue();
        assertThat(column("mk_quote_item", "spec").nullable()).isTrue();
        assertThat(column("mk_quote_item", "length").nullable()).isTrue();

        assertThat(new QuoteSheetItem().getRowType()).isEqualTo(QuoteRowType.PRODUCT);
    }

    @Test
    void v144_editLockTable_hasUniqueSheetAndOwnerSnapshot() {
        assertThat(column("mk_quote_sheet_edit_lock", "sheet_id").type()).isEqualTo("bigint");
        assertThat(column("mk_quote_sheet_edit_lock", "sheet_id").nullable()).isFalse();
        assertThat(column("mk_quote_sheet_edit_lock", "owner_id").nullable()).isFalse();
        assertThat(column("mk_quote_sheet_edit_lock", "owner_name").maxLength()).isEqualTo(64);
        assertThat(column("mk_quote_sheet_edit_lock", "owner_name").nullable()).isFalse();
        assertThat(column("mk_quote_sheet_edit_lock", "expires_at").nullable()).isFalse();
        assertThat(uniqueColumns("mk_quote_sheet_edit_lock", "uk_quote_sheet_edit_lock_sheet"))
                .containsExactly("sheet_id");
    }

    @Test
    void quoteUniqueConstraints_arePresent() {
        assertThat(uniqueColumns("mk_quote_sheet_brand", "uk_quote_sheet_brand"))
                .containsExactly("sheet_id", "brand_name");
        assertThat(uniqueColumns("mk_quote_item", "uk_quote_item_line"))
                .containsExactly("sheet_id", "line_no");
        assertThat(uniqueColumns("mk_quote_item_price", "uk_quote_item_price"))
                .containsExactly("item_id", "brand_name");
        assertThat(uniqueColumns("mk_quote_sheet", "uk_quote_sheet_no"))
                .containsExactly("sheet_no");
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

    private List<String> columnNames(String table) {
        return jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_schema = 'public' and table_name = ?
                """, String.class, table);
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
