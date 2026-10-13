package com.leo.erp.market.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖矩阵「是否有行情」聚合查询的真实 PostgreSQL 回归（默认跳过，设置
 * {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>{@link SteelQuoteRepository#countQuotesByDateAndPeriod} 是覆盖矩阵与取价日历的唯一数据源，
 * 其中「数据源/地区为空即不过滤」写成 JPQL 的 {@code (:param is null or ...)} —— 这条只能在真库上
 * 验证：参数绑定与类型推断出问题时会直接抛异常，而不是返回空集。</p>
 *
 * <p>写用例造数直接走 {@code JdbcTemplate}（实体路径依赖审计填充与母文章外键，与本用例无关），
 * 事务由 {@code @DataJpaTest} 回滚。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SteelQuoteRepositoryPostgresTest {

    private static final LocalDate DAY = LocalDate.of(2099, 3, 2);
    private static final String SOURCE = "PGTEST";
    private static final String MARKET = "测试市场";
    private static final String OTHER_MARKET = "别的市场";
    private static final long ARTICLE_ID = 8_000_000_001L;
    private static final AtomicLong ID_SEQ = new AtomicLong(9_000_000_000L);

    @Autowired
    private SteelQuoteRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    /** 行情明细有指向文章的外键, 造数前先落一条母文章。 */
    @BeforeEach
    void insertArticle() {
        jdbc.update("""
                insert into mk_steel_article (id, article_url, article_date, article_time, title,
                                              period, row_count, market, fetched_at, source)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do nothing
                """,
                ARTICLE_ID, "https://pgtest.local/article", DAY, "0930",
                "PGTEST 行情", "上午", 1, MARKET, LocalDateTime.now(), SOURCE);
    }

    @Test
    void 按日期时段聚合并排除软删行() {
        quote(DAY, "上午", MARKET, false, "Φ12");
        quote(DAY, "上午", MARKET, false, "Φ14");
        quote(DAY, "下午", MARKET, false, "Φ12");
        quote(DAY.plusDays(1), "上午", MARKET, false, "Φ12");
        quote(DAY, "上午", OTHER_MARKET, false, "Φ12");
        quote(DAY, "中午", MARKET, true, "Φ12"); // 软删: 不计入

        Map<String, Long> rows = aggregate(DAY, DAY, SOURCE, MARKET);

        assertThat(rows).containsExactlyInAnyOrderEntriesOf(Map.of(
                DAY + "|上午", 2L,
                DAY + "|下午", 1L));
    }

    @Test
    void 数据源与地区为空即不过滤() {
        quote(DAY, "上午", MARKET, false, "Φ12");
        // 换一个时段, 避免与上面那条在「日期|时段」键上重叠, 断言才看得清两个市场的区别
        quote(DAY, "下午", OTHER_MARKET, false, "Φ12");

        // 两个条件都为空: 两个市场都算进来
        assertThat(aggregate(DAY, DAY, null, null)).containsOnlyKeys(DAY + "|上午", DAY + "|下午");

        // 只给数据源
        assertThat(aggregate(DAY, DAY, SOURCE, null)).containsOnlyKeys(DAY + "|上午", DAY + "|下午");

        // 只给地区
        assertThat(aggregate(DAY, DAY, null, MARKET))
                .containsExactlyEntriesOf(Map.of(DAY + "|上午", 1L));

        // 数据源不匹配: 空集而不是报错
        assertThat(aggregate(DAY, DAY, "别的源", null)).isEmpty();
    }

    @Test
    void 区间外的行情不计入() {
        quote(DAY, "上午", MARKET, false, "Φ12");
        quote(DAY.plusDays(5), "上午", MARKET, false, "Φ12");

        assertThat(aggregate(DAY, DAY, SOURCE, MARKET)).containsOnlyKeys(DAY + "|上午");
        assertThat(aggregate(DAY, DAY.plusDays(5), SOURCE, MARKET))
                .containsOnlyKeys(DAY + "|上午", DAY.plusDays(5) + "|上午");
    }

    /** 聚合结果按 {@code 日期|时段} 归集, 便于断言。 */
    private Map<String, Long> aggregate(LocalDate from, LocalDate to, String source, String market) {
        Map<String, Long> byDatePeriod = new LinkedHashMap<>();
        for (Object[] row : repository.countQuotesByDateAndPeriod(from, to, source, market)) {
            byDatePeriod.put(row[0] + "|" + row[1], ((Number) row[2]).longValue());
        }
        return byDatePeriod;
    }

    /** 造一条行情明细；业务唯一键含规格, 用 {@code spec} 区分同一时段的并行。 */
    private void quote(LocalDate date, String period, String market, boolean deleted, String spec) {
        jdbc.update("""
                insert into mk_steel_quote (id, article_id, source, market, quote_date, period,
                                            breed, spec, material, factory, price, scraped_at, deleted_flag)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ID_SEQ.incrementAndGet(), ARTICLE_ID, SOURCE, market, date, period,
                "螺纹钢", spec, "HRB400E", "测试钢厂",
                new BigDecimal("3400.00"), LocalDateTime.now(), deleted);
    }
}
