package com.leo.erp.common.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V207 / V208 / V209 清理发布的真实库回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>deprecated 整改的回归此前只有 2 列有断言、视图重建与权限清理 0 断言，本用例把三类产物钉住：</p>
 * <ol>
 *   <li><b>V207 + V208：9 个 deprecated 列从 schema 删除</b>（用 (table, column) 对列表一条 SQL 覆盖）；</li>
 *   <li><b>迁移已登记且成功</b>：{@code flyway_schema_history} 的 207/208/209 三行 success=true ——
 *       防止「空库/未迁移环境下列本来就不存在」把 1) 变成恒真断言；</li>
 *   <li><b>V208：{@code global_search_document} 视图重建有效</b>：仍输出原 8 列、可查询、
 *       定义里不再引用 3 个被删列（收窄到 {@code freight_bill.}/{@code freight_statement.} 前缀，
 *       避免误伤 sales_order / receipt 等其它表的同名列）；</li>
 *   <li><b>V208：覆盖索引 {@code idx_lg_freight_bill_list} 重建</b>：INCLUDE 只剩
 *       {@code carrier_name, total_weight, total_freight}，单列 trgm 索引随列删除；</li>
 *   <li><b>V209：台账调整写权限码已清理</b>：只剩 {@code ledger-adjustments:read}，写码 0 授权。</li>
 * </ol>
 *
 * <p>迁移由 Flyway 管理，本用例只读 {@code information_schema} / {@code pg_indexes} /
 * {@code pg_get_viewdef} / {@code flyway_schema_history} / {@code sys_permission}，不写任何数据。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class DeprecatedCleanupV207V208V209PostgresTest {

    /** V207 删 6 列 + V208 删 3 列，共 9 个 (table, column)。 */
    private static final List<String[]> DROPPED_COLUMNS = List.of(
            new String[]{"md_project", "price_closed_day_prev_settle"},
            new String[]{"so_sales_order", "price_closed_settle_prev"},
            new String[]{"so_sales_order", "sales_mode"},
            new String[]{"lg_freight_bill", "source_sales_order_id"},
            new String[]{"lg_freight_bill", "customer_name"},
            new String[]{"lg_freight_bill", "project_name"},
            new String[]{"so_sales_outbound", "source_freight_bill_id"},
            new String[]{"st_freight_statement", "sign_status"},
            new String[]{"st_freight_statement_item", "source_sales_outbound_item_id"}
    );

    /** V208 重建后视图的输出列（名字/顺序/类型均不得变，只把取值换成 NULL）。 */
    private static final List<String> SEARCH_VIEW_COLUMNS = List.of(
            "module_key", "record_id", "primary_no", "summary", "search_text",
            "created_by", "updated_at", "deleted_flag");

    /** V209 清理掉的 4 个写权限码。 */
    private static final List<String> RETIRED_WRITE_CODES = List.of(
            "ledger-adjustments:create",
            "ledger-adjustments:update",
            "ledger-adjustments:delete",
            "ledger-adjustments:audit");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v207v208_九个deprecated列已从schema删除() {
        assertThat(DROPPED_COLUMNS).as("用例必须覆盖全部 9 列").hasSize(9);

        // 反向护栏：9 张表本身必须还在，否则「列不存在」会退化成「表不存在」的假阳性
        List<String> missingTables = DROPPED_COLUMNS.stream()
                .map(pair -> pair[0])
                .distinct()
                .filter(table -> jdbc.queryForObject(
                        "SELECT to_regclass('public.' || ?)", String.class, table) == null)
                .toList();
        assertThat(missingTables).as("被清理列所在表必须仍然存在").isEmpty();

        String droppedPairs = DROPPED_COLUMNS.stream()
                .map(pair -> "('" + pair[0] + "', '" + pair[1] + "')")
                .collect(Collectors.joining(", "));

        List<String> stillPresent = jdbc.query("""
                SELECT column_info.table_name || '.' || column_info.column_name
                  FROM information_schema.columns column_info
                  JOIN (VALUES %s) AS dropped(table_name, column_name)
                    ON column_info.table_schema = 'public'
                   AND column_info.table_name = dropped.table_name
                   AND column_info.column_name = dropped.column_name
                """.formatted(droppedPairs), (resultSet, rowNumber) -> resultSet.getString(1));

        assertThat(stillPresent).as("V207/V208 应删除的 deprecated 列仍存在于 schema 中").isEmpty();
    }

    @Test
    void v207v208v209_迁移已登记且执行成功() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT version, success
                  FROM flyway_schema_history
                 WHERE version IN ('207', '208', '209')
                 ORDER BY installed_rank
                """);

        assertThat(rows).as("V207/V208/V209 必须各自登记一行").hasSize(3);
        assertThat(rows)
                .extracting(row -> String.valueOf(row.get("version")))
                .containsExactly("207", "208", "209");
        assertThat(rows).allSatisfy(row -> assertThat(row.get("success"))
                .as("V%s success", row.get("version"))
                .isEqualTo(Boolean.TRUE));
    }

    @Test
    void v208_全局搜索视图重建后仍输出八列且不再引用被删列() {
        List<String> viewColumns = jdbc.queryForList("""
                SELECT column_name
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = 'global_search_document'
                 ORDER BY ordinal_position
                """, String.class);
        assertThat(viewColumns).containsExactlyElementsOf(SEARCH_VIEW_COLUMNS);

        // 视图可查询（不止能编译），且行数是非负整数
        Integer totalRows = jdbc.queryForObject(
                "SELECT count(*) FROM public.global_search_document", Integer.class);
        assertThat(totalRows).isNotNull().isGreaterThanOrEqualTo(0);

        String viewDefinition = jdbc.queryForObject(
                "SELECT pg_get_viewdef('public.global_search_document'::regclass, true)", String.class);
        assertThat(viewDefinition)
                .as("V208 必须把 3 个被删列的取值替换为 NULL::text")
                .isNotNull()
                .doesNotContain("freight_bill.customer_name",
                        "freight_bill.project_name",
                        "freight_statement.sign_status")
                // 反向防假阴性：被改写的两个 UNION ALL 分支与输出列必须仍在定义里
                .contains("freight_bill.", "freight_statement.", "summary", "search_text");
    }

    @Test
    void v208_覆盖索引重建后INCLUDE不再包含被删列() {
        String indexDefinition = jdbc.queryForObject("""
                SELECT indexdef
                  FROM pg_indexes
                 WHERE schemaname = 'public'
                   AND indexname = 'idx_lg_freight_bill_list'
                """, String.class);

        assertThat(indexDefinition)
                .as("覆盖索引必须显式去掉 customer_name / project_name 后重建")
                .isNotNull()
                .contains("INCLUDE (carrier_name, total_weight, total_freight)")
                .doesNotContain("customer_name", "project_name");

        Integer trigramIndexCount = jdbc.queryForObject("""
                SELECT count(*)
                  FROM pg_indexes
                 WHERE schemaname = 'public'
                   AND indexname = 'idx_lg_freight_bill_customer_name_trgm'
                """, Integer.class);
        assertThat(trigramIndexCount).as("customer_name 上的 trgm 索引应随列删除").isZero();
    }

    @Test
    void v209_台账调整只剩只读权限且写权限无角色授权() {
        List<String> codes = jdbc.queryForList("""
                SELECT code
                  FROM sys_permission
                 WHERE code LIKE 'ledger-adjustments:%'
                 ORDER BY code
                """, String.class);

        assertThat(codes)
                .as("V209 删除 4 个写权限码后，任何形态的库都不得再出现它们")
                .doesNotContain(RETIRED_WRITE_CODES.toArray(String[]::new));
        // dev/生产形态（应用启动同步过权限目录）恰好剩一行 :read；
        // CI 全新库由 Flyway 建库、不播种 sys_permission，允许为空
        assertThat(codes).allSatisfy(code -> assertThat(code).isEqualTo("ledger-adjustments:read"));

        Integer writeRoleGrants = jdbc.queryForObject("""
                SELECT count(*)
                  FROM sys_role_permission
                 WHERE permission_code IN ('ledger-adjustments:create', 'ledger-adjustments:update',
                                           'ledger-adjustments:delete', 'ledger-adjustments:audit')
                   AND deleted_flag = false
                """, Integer.class);
        assertThat(writeRoleGrants).as("写权限码不得有任何角色授权").isZero();
    }
}
