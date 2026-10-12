package com.leo.erp.market.repository;

import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.market.domain.entity.SteelQuoteClosedDay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 休市日仓储与 V177 迁移的真实 PostgreSQL 回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>覆盖: 表结构与唯一键与实体映射一致; 四个派生查询都能被 Spring Data 解析(SELECT 条件下推,
 * 不做全表过滤); (数据源, 市场, 日期) 唯一 —— 重复写入必须被数据库拦下, 服务层的幂等判断只是
 * 提前返回而不是唯一防线。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditConfig.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SteelQuoteClosedDayRepositoryPostgresTest {

    private static final String SOURCE = "LEO_TEST_CLOSED";
    private static final String MARKET = "测试市场";
    private static final String OTHER_MARKET = "测试市场二";
    private static final LocalDate DAY = LocalDate.of(2099, 2, 1);

    @Autowired
    private SteelQuoteClosedDayRepository repository;

    private SteelQuoteClosedDay row(long id, String market, LocalDate date) {
        SteelQuoteClosedDay row = new SteelQuoteClosedDay();
        row.setId(id);
        row.setSource(SOURCE);
        row.setMarket(market);
        row.setQuoteDate(date);
        row.setReason("集成测试");
        return row;
    }

    @Test
    void 四个查询条件组合都能命中且互不越界() {
        repository.saveAll(List.of(
                row(941000000000000101L, MARKET, DAY),
                row(941000000000000102L, OTHER_MARKET, DAY.plusDays(1))));

        assertThat(repository.findBySourceAndMarketAndQuoteDateBetweenAndDeletedFlagFalse(
                SOURCE, MARKET, DAY, DAY.plusDays(2))).hasSize(1);
        assertThat(repository.findBySourceAndQuoteDateBetweenAndDeletedFlagFalse(
                SOURCE, DAY, DAY.plusDays(2))).hasSize(2);
        assertThat(repository.findByMarketAndQuoteDateBetweenAndDeletedFlagFalse(
                OTHER_MARKET, DAY, DAY.plusDays(2))).hasSize(1);
        assertThat(repository.findByQuoteDateBetweenAndDeletedFlagFalse(DAY, DAY.plusDays(2)))
                .hasSize(2);
        // 区间外不返回
        assertThat(repository.findByQuoteDateBetweenAndDeletedFlagFalse(DAY.plusDays(3), DAY.plusDays(4)))
                .isEmpty();
    }

    @Test
    void 唯一键拦住同源同市场同日的重复记录() {
        repository.saveAndFlush(row(941000000000000111L, MARKET, DAY));

        assertThatThrownBy(() -> repository.saveAndFlush(row(941000000000000112L, MARKET, DAY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 物理删除后同日可重新记录() {
        repository.saveAndFlush(row(941000000000000121L, MARKET, DAY));
        repository.findBySourceAndMarketAndQuoteDate(SOURCE, MARKET, DAY)
                .ifPresent(repository::delete);
        repository.flush();

        assertThat(repository.findBySourceAndMarketAndQuoteDate(SOURCE, MARKET, DAY)).isEmpty();
        repository.saveAndFlush(row(941000000000000122L, MARKET, DAY));
        assertThat(repository.findBySourceAndMarketAndQuoteDate(SOURCE, MARKET, DAY)).isPresent();
    }
}
