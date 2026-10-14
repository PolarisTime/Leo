package com.leo.erp.common.persistence;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 关键字谓词与排除集合的回归（真实 PostgreSQL，默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>覆盖两件事：</p>
 * <ol>
 *   <li>关键字谓词必须渲染成裸列 {@code ILIKE}（而不是 {@code lower(col) LIKE}），这样才能命中 V1 里
 *       那些建在裸列上的 partial {@code gin_trgm_ops} 索引；用例同时用临时表 + {@code enable_seqscan=off}
 *       锁住"ILIKE 能走 trgm 索引、lower(col) LIKE 不能"这条前提。</li>
 *   <li>排除集合（{@code Specs.idNotIn}）里的 {@code null} 不得让 {@code NOT IN} 静默清空结果集
 *       （{@code id NOT IN (?, NULL)} 对任何行都是 UNKNOWN）。</li>
 * </ol>
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
class SpecsKeywordAndExclusionPostgresTest {

    private static final String TRGM_INDEX = "probe_kw_order_no_trgm";
    private static final int PROBE_ROWS = 20_000;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void keywordSpecRendersBareColumnIlikeSoTrigramIndexCanBeUsed() {
        Specification<SalesOrder> spec = Specs.<SalesOrder>notDeleted()
                .and(Specs.keywordLike("so2026", "orderNo", "customerName"));

        String sql = captureSql(() -> salesOrderRepository.findAll(spec, PageRequest.of(0, 1)));

        assertThat(sql)
                .as("关键字谓词必须是裸列 ILIKE，否则 lower(col) 无法匹配裸列 trgm 索引，实际 SQL: %s", sql)
                .contains(" ilike ");
        assertThat(sql)
                .as("关键字谓词不得再包 lower(col)，实际 SQL: %s", sql)
                .doesNotContain("lower(");
    }

    @Test
    void keywordPatternKeepsCaseFoldingContract() {
        assertThat(Specs.containsLikePatternIgnoreCase("  SO2026  ")).isEqualTo("%so2026%");
        assertThat(Specs.containsLikePatternIgnoreCase("a%b_c")).isEqualTo("%a\\%b\\_c%");
        assertThat(Specs.containsLikePatternIgnoreCase("   ")).isNull();
    }

    /**
     * 锁定"裸列 ILIKE ↔ 裸列 partial trgm 索引"这条前提：语料全小写，用大写关键字查询，
     * 既验证 ILIKE 的大小写不敏感，也验证计划确实走了 {@code gin_trgm_ops} 索引；
     * 同样条件下 {@code lower(col) LIKE} 拿不到任何索引，只能顺序扫描。
     */
    @Test
    void bareColumnIlikeUsesTrigramIndexWhileLowerLikeCannot() {
        jdbc.execute("create temp table probe_kw (id bigint, order_no varchar(64), deleted_flag boolean)");
        jdbc.execute("insert into probe_kw "
                + "select g, 'so2026' || lpad(g::text, 8, '0'), false "
                + "from generate_series(1, " + PROBE_ROWS + ") g");
        jdbc.execute("create index " + TRGM_INDEX
                + " on probe_kw using gin (order_no public.gin_trgm_ops) where deleted_flag = false");
        jdbc.execute("analyze probe_kw");
        jdbc.execute("set local enable_seqscan = off");

        String ilikePlan = explain("select count(*) from probe_kw "
                + "where deleted_flag = false and order_no ilike '%2026000077%'");
        String lowerPlan = explain("select count(*) from probe_kw "
                + "where deleted_flag = false and lower(order_no) like '%2026000077%'");

        assertThat(ilikePlan)
                .as("裸列 ILIKE 应由 trgm 索引提供索引条件(~~* 是 ILIKE 的运算符)，实际计划: %s", ilikePlan)
                .contains(TRGM_INDEX)
                .contains("~~*");
        assertThat(lowerPlan)
                .as("lower(col) LIKE 无法匹配裸列索引表达式，索引条件里不应出现 ~~*，实际计划: %s", lowerPlan)
                .doesNotContain("~~*");
    }

    @Test
    void excludedNullElementDoesNotSilentlyEmptyResult() {
        Long existingId = firstSalesOrderId();
        assumeTrue(existingId != null, "开发库没有销售订单数据，跳过排除集合断言");

        long total = salesOrderRepository.count();
        long remaining = salesOrderRepository.count(Specs.idNotIn(Arrays.asList(null, existingId)));

        assertThat(remaining)
                .as("排除集合里混入 null 时，NOT IN 不得整体失效（只应排除那 1 条）")
                .isEqualTo(total - 1);
    }

    @Test
    void excludedNullOnlyCollectionIsNoOp() {
        long total = salesOrderRepository.count();
        long remaining = salesOrderRepository.count(Specs.idNotIn(Collections.singletonList(null)));

        assertThat(remaining)
                .as("排除集合只有 null 元素时应等价于不排除（NOT IN (NULL) 会清空结果集）")
                .isEqualTo(total);
    }

    private Long firstSalesOrderId() {
        List<Long> ids = jdbc.queryForList("select id from so_sales_order order by id limit 1", Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String captureSql(Runnable query) {
        SqlCaptureInspector.reset();
        query.run();
        return SqlCaptureInspector.capturedSql().toLowerCase(Locale.ROOT);
    }

    private String explain(String sql) {
        return String.join("\n", jdbc.queryForList("explain (analyze, buffers) " + sql, String.class));
    }
}
