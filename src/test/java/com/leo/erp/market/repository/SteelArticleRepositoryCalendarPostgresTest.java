package com.leo.erp.market.repository;

import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.market.domain.entity.SteelArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行情文章仓储的派生查询回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>覆盖「行情日历按数据源过滤」的关键分支: `market` 是 NOT NULL 列, 未指定地区时只能走
 * 「只按数据源」的查询; 若沿用 `source + market = null`, SQL 会退化成 `market IS NULL` 而
 * 恒为空集, 覆盖矩阵与近 30 天覆盖统计会整片显示成"缺失"(真实缺陷的成因)。</p>
 *
 * <p>用例自带隔离数据(固定 id/URL + 事务回滚), 不依赖开发库既有行情文章。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditConfig.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SteelArticleRepositoryCalendarPostgresTest {

    private static final String SOURCE = "LEO_TEST_CAL";
    private static final String MARKET_A = "测试甲";
    private static final String MARKET_B = "测试乙";
    /** 远离真实行情的日期区间, 避免与开发库既有数据相互干扰。 */
    private static final LocalDate FROM = LocalDate.of(2099, 1, 1);
    private static final LocalDate TO = LocalDate.of(2099, 1, 5);

    @Autowired
    private SteelArticleRepository articleRepository;

    private SteelArticle article(long id, String market, LocalDate date) {
        SteelArticle article = new SteelArticle();
        article.setId(id);
        article.setArticleUrl("https://e2e.leo/calendar/" + id);
        article.setArticleDate(date);
        article.setArticleTime("10:30");
        article.setTitle("行情日历测试 " + market);
        article.setPeriod("上午");
        article.setRowCount(100);
        article.setSource(SOURCE);
        article.setMarket(market);
        article.setFetchedAt(LocalDateTime.now());
        return article;
    }

    @BeforeEach
    void setUp() {
        articleRepository.saveAll(List.of(
                article(941000000000000001L, MARKET_A, FROM),
                article(941000000000000002L, MARKET_B, FROM.plusDays(1))));
    }

    @Test
    void 只按数据源过滤返回该源全部市场的文章() {
        List<SteelArticle> rows = articleRepository
                .findBySourceAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
                        SOURCE, FROM, TO);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(SteelArticle::getMarket)
                .containsExactly(MARKET_A, MARKET_B);
    }

    @Test
    void 数据源加空地区会退化成空集_因此服务层必须归一化空地区() {
        // 直传 null 的旧路径: market IS NULL 在 NOT NULL 列上恒不匹配 —— 这就是
        // 「数据源已选、地区未选」时日历整片缺失的成因, 保留断言防止有人再透传空值。
        List<SteelArticle> rows = articleRepository
                .findBySourceAndMarketAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
                        SOURCE, null, FROM, TO);

        assertThat(rows).isEmpty();
    }

    @Test
    void 只按地区过滤返回该地区全部数据源的文章() {
        List<SteelArticle> rows = articleRepository
                .findByMarketAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
                        MARKET_A, FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSource()).isEqualTo(SOURCE);
        assertThat(rows.get(0).getArticleDate()).isEqualTo(FROM);
    }
}
