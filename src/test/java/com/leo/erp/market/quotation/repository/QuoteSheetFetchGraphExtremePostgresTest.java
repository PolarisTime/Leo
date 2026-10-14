package com.leo.erp.market.quotation.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.service.QuoteSheetStore;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的懒加载/多 bag/N+1 极端回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>
 * 断言详情与分页在 {@code open-in-view=false} 语义下不会抛 {@code LazyInitializationException},
 * 且集合按 {@code @BatchSize(default_batch_fetch_size=50)} 批量抓取, 分页映射不会产生 N+1。
 * 每个单据含 2 品牌、2 明细、每明细 2 现货价; 以 Hibernate Statistics 的 prepared statement 数作为上界证据。
 * <p>
 * 开发库是共享资源(含历史 E2E 单据), 因此所有分页断言只用单据名前缀 {@code pg-fetch-extreme-} 收窄到
 * 本用例自建的单据, 既不清理他人数据, 也不会被历史数据带成假红。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({QuoteSheetStore.class, JpaAuditConfig.class, QuoteSheetFetchGraphExtremePostgresTest.StubConfig.class})
class QuoteSheetFetchGraphExtremePostgresTest {

    private static final long BASE_ID = 950000000000000000L;
    private static final int SHEET_COUNT = 4;
    /** 本用例自建单据名/单据号前缀: 共享开发库上的唯一归属标记。 */
    private static final String NAME_PREFIX = "pg-fetch-extreme-";

    @Autowired
    private QuoteSheetStore store;

    @Autowired
    private QuoteSheetRepository repository;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        if (!createdIds.isEmpty()) {
            repository.deleteAllById(createdIds);
            createdIds.clear();
        }
    }

    @Test
    void detail_initializesBrandsItemsAndPricesWithinTransaction() {
        persistSheets();
        entityManager.clear();

        for (Long id : createdIds) {
            QuoteSheetResponse detail = store.detail(id);
            assertThat(detail.brands()).hasSize(2);
            assertThat(detail.items()).hasSize(2);
            assertThat(detail.items()).allSatisfy(item -> assertThat(item.prices()).hasSize(2));
        }
    }

    /** 分页映射全部集合后, prepared statement 数必须有界(无按单据逐条 N+1)。 */
    @Test
    void page_mapsAllCollectionsWithoutNPlusOne() {
        persistSheets();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Page<QuoteSheetResponse> page = pageCreatedSheets();

        assertThat(createdIdsOf(page))
                .as("共享开发库上的分页必须只统计本用例自建单据; 历史 E2E 单据不得进入统计")
                .containsExactlyInAnyOrderElementsOf(createdIds);
        assertThat(page.getContent()).allSatisfy(sheet -> {
            assertThat(sheet.brands()).hasSize(2);
            assertThat(sheet.items()).hasSize(2);
            assertThat(sheet.items()).allSatisfy(item -> assertThat(item.prices()).hasSize(2));
        });
        assertThat(statistics.getPrepareStatementCount())
                .as("分页 + 批量抓取 brands/items/prices 的 SQL 数应有界; 实际=%s", statistics.getPrepareStatementCount())
                .isLessThanOrEqualTo(8L);
    }

    /** 批量抓取配置: 集合查询次数应与单据数无关(1 张 vs 4 张 prepared statement 数一致)。 */
    @Test
    void page_collectionStatementsDoNotScaleWithSheetCount() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        persistSheet(0);
        entityManager.clear();
        statistics.clear();
        Page<QuoteSheetResponse> oneSheetPage = pageCreatedSheets();
        long withOneSheet = statistics.getPrepareStatementCount();
        assertThat(createdIdsOf(oneSheetPage))
                .as("收窄后的分页必须真的命中本用例自建单据, 否则 SQL 数相等只是空跑")
                .containsExactly(createdIds.get(0));

        for (int i = 1; i < SHEET_COUNT; i++) {
            persistSheet(i);
        }
        entityManager.clear();
        statistics.clear();
        Page<QuoteSheetResponse> fourSheetsPage = pageCreatedSheets();
        long withFourSheets = statistics.getPrepareStatementCount();
        assertThat(createdIdsOf(fourSheetsPage)).containsExactlyInAnyOrderElementsOf(createdIds);

        assertThat(withFourSheets)
                .as("按单据数线性增长的 SQL 数意味着 N+1; one=%s four=%s", withOneSheet, withFourSheets)
                .isEqualTo(withOneSheet);
    }

    /** 只取本用例自建单据的分页: 用名称前缀把查询收窄在自有数据上, 不依赖(也不清理)共享库的历史数据。 */
    private Page<QuoteSheetResponse> pageCreatedSheets() {
        return store.page(new PageQuery(0, 20, null, null), null, null, NAME_PREFIX);
    }

    private static List<Long> createdIdsOf(Page<QuoteSheetResponse> page) {
        return page.getContent().stream().map(QuoteSheetResponse::id).toList();
    }

    private void persistSheets() {
        for (int i = 0; i < SHEET_COUNT; i++) {
            persistSheet(i);
        }
        // 触发一次只读查询确认 schema 可用(避免统计口径受首次连接初始化影响)。
        jdbc.queryForObject("select 1", Integer.class);
    }

    private void persistSheet(int i) {
        LocalDate orderDate = LocalDate.of(2026, 9, 16);
        long sheetId = BASE_ID + i * 100L;
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(sheetId);
        sheet.setSheetNo("PG-FG-" + sheetId);
        sheet.setName(NAME_PREFIX + i);
        sheet.setProjectName("云潮筝鸣府");
        sheet.setOrderDate(orderDate);
        sheet.setRefDate(orderDate);
        sheet.setRefPeriod("09:00");
        sheet.setLengthPremium(new BigDecimal("30"));
        sheet.setStatus("报价");
        sheet.setCreatedAt(LocalDateTime.now());
        sheet.setCreatedBy(0L);
        sheet.setCreatedName("system");

        for (int b = 0; b < 2; b++) {
            QuoteSheetBrand brand = new QuoteSheetBrand();
            brand.setId(sheetId + 1 + b);
            brand.setSheet(sheet);
            brand.setBrandName("品牌" + b);
            brand.setFreight(BigDecimal.ZERO);
            brand.setSortOrder(b);
            sheet.getBrands().add(brand);
        }
        for (int itemIndex = 0; itemIndex < 2; itemIndex++) {
            QuoteSheetItem item = new QuoteSheetItem();
            item.setId(sheetId + 11 + itemIndex);
            item.setSheet(sheet);
            item.setLineNo(itemIndex + 1);
            item.setCategory("螺纹钢");
            item.setMaterial("HRB400E");
            item.setSpec(12);
            item.setLength("9米");
            item.setTon(BigDecimal.TEN);
            for (int p = 0; p < 2; p++) {
                QuoteSheetItemPrice price = new QuoteSheetItemPrice();
                price.setId(sheetId + 21 + itemIndex * 10L + p);
                price.setItem(item);
                price.setBrandName("品牌" + p);
                price.setSpotPrice(new BigDecimal("3600"));
                item.getPrices().add(price);
            }
            sheet.getItems().add(item);
        }
        repository.saveAndFlush(sheet);
        createdIds.add(sheetId);
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        SupplierQuery supplierQuery() {
            return Mockito.mock(SupplierQuery.class);
        }

        @Bean
        ProjectQuery projectQuery() {
            return Mockito.mock(ProjectQuery.class);
        }

        @Bean
        PurchaseOrderOptionQuery purchaseOrderOptionQuery() {
            return Mockito.mock(PurchaseOrderOptionQuery.class);
        }

        @Bean
        SnowflakeIdGenerator snowflakeIdGenerator() {
            return new SnowflakeIdGenerator(0L, false);
        }
    }
}
