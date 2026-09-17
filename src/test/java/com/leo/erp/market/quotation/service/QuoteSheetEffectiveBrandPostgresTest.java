package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.SupplierQuery;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 PostgreSQL 回归: 现货价品牌校验以"项目配置品牌"为唯一真源
 * (默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>
 * 真源判定口径: 单据 {@code projectId != null} 且项目配置存在且至少含一个非空品牌名时, 只认配置品牌;
 * 仅当 {@code projectId == null}、配置不存在、或配置品牌为空时, 回退单据 {@code mk_quote_sheet_brand} 快照。
 * 替换上一版"快照 ∪ 配置"的并集放行, 配置删除某品牌后该品牌价格写入必须 422。
 * <p>
 * 写单据时把快照与配置全量对齐(补齐缺失、删除多余、同步运费/排序), 仅在确有差异时变更,
 * 且父版本任意路径恰好 +1、响应版本与数据库回读一致。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({QuoteSheetStore.class, JpaAuditConfig.class, QuoteSheetEffectiveBrandPostgresTest.StubConfig.class})
class QuoteSheetEffectiveBrandPostgresTest {

    private static final long SHEET_ID = 930000000000000101L;
    private static final long ITEM_ID = 930000000000000102L;
    private static final long PRICE_ID = 930000000000000104L;
    private static final long SNAPSHOT_BRAND_ID = 930000000000000105L;
    private static final long PROJECT_ID = 930000000000000301L;
    private static final long CONFIG_ID = 930000000000000302L;
    private static final long CONFIG_BRAND_ID_1 = 930000000000000303L;
    private static final long CONFIG_BRAND_ID_2 = 930000000000000304L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 9, 17);
    private static final BigDecimal LENGTH_PREMIUM = new BigDecimal("30");
    private static final String BASE_BRAND = "中天";
    private static final String CONFIG_ONLY_BRAND = "铜陵富鑫";
    private static final String STALE_BRAND = "旧品牌";
    private static final String UNKNOWN_BRAND = "未知品牌";

    @Autowired
    private QuoteSheetStore store;

    @Autowired
    private QuoteSheetRepository sheetRepository;

    @Autowired
    private QuoteProjectConfigRepository configRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 在只读事务内访问懒加载集合(写用例以 NOT_SUPPORTED 真实提交, 会话已关闭)。 */
    private <T> T readInTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    @AfterEach
    void cleanup() {
        if (sheetRepository.existsById(SHEET_ID)) {
            sheetRepository.deleteById(SHEET_ID);
        }
        if (configRepository.existsById(CONFIG_ID)) {
            configRepository.deleteById(CONFIG_ID);
        }
    }

    /** 配置含新品牌: 行级写该品牌 200, 快照被补齐为新集合, 父版本恰好 +1 且与回读一致。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rowItemWrite_configOnlyBrand_isAcceptedAndSnapshotSynced() {
        persistSheet(PROJECT_ID, BASE_BRAND);
        persistConfig(BASE_BRAND, CONFIG_ONLY_BRAND);
        long version = dbVersion();

        QuoteSheetItemWrite added = store.addItem(SHEET_ID,
                itemRequest("盘螺", "HRB400E", 8, "3300", CONFIG_ONLY_BRAND), version);

        assertThat(added.item().prices()).extracting(QuoteSheetResponse.ItemPriceResponse::brandName)
                .containsExactly(CONFIG_ONLY_BRAND);
        List<String> persistedPriceBrands = readInTransaction(() -> sheetRepository
                .findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getItems().stream()
                .flatMap(item -> item.getPrices().stream())
                .map(QuoteSheetItemPrice::getBrandName)
                .toList());
        assertThat(persistedPriceBrands).contains(BASE_BRAND, CONFIG_ONLY_BRAND);
        assertThat(dbBrandNames()).containsExactly(BASE_BRAND, CONFIG_ONLY_BRAND);
        assertThat(dbVersion()).isEqualTo(version + 1);
        assertThat(store.currentVersion(SHEET_ID)).isEqualTo(version + 1);
    }

    /**
     * 关键回归(替换上一版并集放行): 配置已删除某品牌后, 即使该品牌仍在单据快照中,
     * 再写该品牌价格也必须 422, 且版本不推进。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rowItemWrite_configDeletedBrand_isRejected422() {
        persistSheet(PROJECT_ID, BASE_BRAND, CONFIG_ONLY_BRAND);
        persistConfig(BASE_BRAND);
        long version = dbVersion();

        assertThatThrownBy(() -> store.addItem(SHEET_ID,
                itemRequest("盘螺", "HRB400E", 8, "3300", CONFIG_ONLY_BRAND), version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(dbVersion()).isEqualTo(version);
    }

    /** 快照中残留、配置已无的品牌: 写入被拒, 且随下一次成功写的快照同步被移除。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rowItemWrite_staleSnapshotBrand_isRejectedAndRemovedBySync() {
        persistSheet(PROJECT_ID, BASE_BRAND, STALE_BRAND);
        persistConfig(BASE_BRAND, CONFIG_ONLY_BRAND);
        long version = dbVersion();

        assertThatThrownBy(() -> store.addItem(SHEET_ID,
                itemRequest("盘螺", "HRB400E", 8, "3300", STALE_BRAND), version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(dbVersion()).isEqualTo(version);
        assertThat(dbBrandNames()).containsExactly(BASE_BRAND, STALE_BRAND);

        store.addItem(SHEET_ID, itemRequest("盘螺", "HRB400E", 8, "3300", CONFIG_ONLY_BRAND), version);

        assertThat(dbBrandNames()).containsExactly(BASE_BRAND, CONFIG_ONLY_BRAND);
        assertThat(dbVersion()).isEqualTo(version + 1);
    }

    /** 整单替换: 快照与配置完全对齐(删除多余、补齐缺失), 父版本恰好 +1。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void wholeReplace_alignsSnapshotToConfigAndVersionPlusOne() {
        persistSheet(PROJECT_ID, BASE_BRAND, STALE_BRAND);
        persistConfig(BASE_BRAND, CONFIG_ONLY_BRAND);
        long version = dbVersion();

        QuoteSheetRequest request = new QuoteSheetRequest(
                "pg-effective-brand", PROJECT_ID, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                LENGTH_PREMIUM, false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest(BASE_BRAND, BigDecimal.ZERO, 0)),
                List.of(itemRequest("盘螺", "HRB400E", 8, "3300", CONFIG_ONLY_BRAND)));

        store.update(SHEET_ID, request, version);

        assertThat(dbBrandNames()).containsExactly(BASE_BRAND, CONFIG_ONLY_BRAND);
        assertThat(dbVersion()).isEqualTo(version + 1);
        assertThat(store.currentVersion(SHEET_ID)).isEqualTo(version + 1);
    }

    /** 表头-only: 快照有漂移时恰好 +1, 表头与快照均无变化时不得自增。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void headerOnly_snapshotDriftIncrementsOnceThenNoChangeStays() {
        persistSheet(PROJECT_ID, BASE_BRAND, STALE_BRAND);
        persistConfig(BASE_BRAND, CONFIG_ONLY_BRAND);
        long version = dbVersion();

        QuoteSheetRequest headerOnly = new QuoteSheetRequest(
                "pg-effective-brand", PROJECT_ID, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                LENGTH_PREMIUM, false, false, "报价", null, null, null);

        store.update(SHEET_ID, headerOnly, version);

        assertThat(dbBrandNames()).containsExactly(BASE_BRAND, CONFIG_ONLY_BRAND);
        assertThat(dbVersion()).isEqualTo(version + 1);
        assertThat(store.currentVersion(SHEET_ID)).isEqualTo(version + 1);

        QuoteSheetResponse second = store.update(SHEET_ID, headerOnly, version + 1);

        assertThat(dbVersion()).isEqualTo(version + 1);
        assertThat(second.version()).isEqualTo(version + 1);
    }

    /** 回退: projectId == null 时按单据快照校验, 配置不参与。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void projectIdNull_fallsBackToSnapshotBrands() {
        persistSheet(null, BASE_BRAND, CONFIG_ONLY_BRAND);
        long version = dbVersion();

        store.addItem(SHEET_ID, itemRequest("盘螺", "HRB400E", 8, "3300", CONFIG_ONLY_BRAND), version);
        assertThat(dbVersion()).isEqualTo(version + 1);

        assertThatThrownBy(() -> store.addItem(SHEET_ID,
                itemRequest("盘螺", "HRB400E", 8, "3300", UNKNOWN_BRAND), version + 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中");
    }

    /** 回退: 项目配置不存在时按单据快照校验, 配置独有品牌不可用。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void configMissing_fallsBackToSnapshotBrands() {
        persistSheet(PROJECT_ID, BASE_BRAND);
        long version = dbVersion();

        store.addItem(SHEET_ID, itemRequest("盘螺", "HRB400E", 8, "3300", BASE_BRAND), version);
        assertThat(dbVersion()).isEqualTo(version + 1);

        assertThatThrownBy(() -> store.addItem(SHEET_ID,
                itemRequest("盘螺", "HRB400E", 8, "3300", CONFIG_ONLY_BRAND), version + 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中");
    }

    /** 回退: 配置存在但品牌集合为空时按单据快照校验。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void configBrandsEmpty_fallsBackToSnapshotBrands() {
        persistSheet(PROJECT_ID, BASE_BRAND);
        persistConfig();
        long version = dbVersion();

        store.addItem(SHEET_ID, itemRequest("盘螺", "HRB400E", 8, "3300", BASE_BRAND), version);
        assertThat(dbVersion()).isEqualTo(version + 1);

        assertThatThrownBy(() -> store.addItem(SHEET_ID,
                itemRequest("盘螺", "HRB400E", 8, "3300", CONFIG_ONLY_BRAND), version + 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中");
    }

    /** 连续行级写(新增/整行替换/删除)父版本严格每次 +1。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void consecutiveRowWrites_incrementExactlyOnce() {
        persistSheet(PROJECT_ID, BASE_BRAND);
        persistConfig(BASE_BRAND, CONFIG_ONLY_BRAND);
        long version = dbVersion();

        QuoteSheetItemWrite added = store.addItem(SHEET_ID,
                itemRequest("盘螺", "HRB400E", 8, "3300", CONFIG_ONLY_BRAND), version);
        assertThat(dbVersion()).isEqualTo(version + 1);

        QuoteSheetItemWrite updated = store.updateItem(SHEET_ID, added.item().id(),
                itemRequest("盘螺", "HRB400E", 8, "3400", CONFIG_ONLY_BRAND), version + 1);
        assertThat(dbVersion()).isEqualTo(version + 2);

        store.deleteItem(SHEET_ID, updated.item().id(), version + 2);
        assertThat(dbVersion()).isEqualTo(version + 3);
    }

    private long dbVersion() {
        return sheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();
    }

    private List<String> dbBrandNames() {
        return readInTransaction(() -> sheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow()
                .getBrands().stream().map(QuoteSheetBrand::getBrandName).toList());
    }

    private void persistSheet(Long projectId, String... snapshotBrandNames) {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setSheetNo("PG-EFF-" + SHEET_ID);
        sheet.setName("pg-effective-brand");
        sheet.setProjectId(projectId);
        sheet.setProjectName("云潮筝鸣府");
        sheet.setOrderDate(ORDER_DATE);
        sheet.setRefDate(ORDER_DATE);
        sheet.setRefPeriod("09:00");
        sheet.setLengthPremium(LENGTH_PREMIUM);
        sheet.setStatus("报价");
        sheet.setCreatedAt(LocalDateTime.now());
        sheet.setCreatedBy(0L);
        sheet.setCreatedName("system");

        int index = 0;
        for (String brandName : snapshotBrandNames) {
            QuoteSheetBrand brand = new QuoteSheetBrand();
            brand.setId(SNAPSHOT_BRAND_ID + index);
            brand.setSheet(sheet);
            brand.setBrandName(brandName);
            brand.setFreight(BigDecimal.ZERO);
            brand.setSortOrder(index);
            sheet.getBrands().add(brand);
            index += 1;
        }

        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(ITEM_ID);
        item.setSheet(sheet);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setTon(BigDecimal.TEN);
        QuoteSheetItemPrice price = new QuoteSheetItemPrice();
        price.setId(PRICE_ID);
        price.setItem(item);
        price.setBrandName(BASE_BRAND);
        price.setSpotPrice(new BigDecimal("3500"));
        item.getPrices().add(price);
        sheet.getItems().add(item);
        sheetRepository.saveAndFlush(sheet);
    }

    private void persistConfig(String... configBrandNames) {
        QuoteProjectConfig config = new QuoteProjectConfig();
        config.setId(CONFIG_ID);
        config.setProjectId(PROJECT_ID);
        config.setLengthPremium(LENGTH_PREMIUM);
        config.setCreatedAt(LocalDateTime.now());
        config.setCreatedBy(0L);
        config.setCreatedName("system");
        int index = 0;
        for (String brandName : configBrandNames) {
            QuoteProjectBrand brand = new QuoteProjectBrand();
            brand.setId(CONFIG_BRAND_ID_1 + index);
            brand.setConfig(config);
            brand.setBrandName(brandName);
            brand.setFreight(BigDecimal.ZERO);
            brand.setSortOrder(index);
            config.getBrands().add(brand);
            index += 1;
        }
        configRepository.saveAndFlush(config);
    }

    private QuoteSheetRequest.ItemRequest itemRequest(String category, String material, int spec,
                                                      String spotPrice, String priceBrand) {
        return new QuoteSheetRequest.ItemRequest(category, material, spec, "9米", BigDecimal.ONE,
                List.of(new QuoteSheetRequest.ItemPriceRequest(priceBrand, new BigDecimal(spotPrice), null)));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        SupplierQuery supplierQuery() {
            return Mockito.mock(SupplierQuery.class);
        }

        @Bean
        SnowflakeIdGenerator snowflakeIdGenerator() {
            return new SnowflakeIdGenerator(0L, false);
        }
    }
}
