package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 PostgreSQL 极端输入/子实体协调回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>
 * 覆盖: 行号空洞 {2,3}/{1,3} 下整单替换按既有行升序复用并归一化行号, 不触发
 * {@code uk_quote_item_line} 瞬时冲突; 同一请求内"解锁规格数量锁 + 改规格"当前语义;
 * 品牌/prices 的 trim/同名/大小写/超长在极端输入下不得落唯一键 409。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({QuoteSheetStore.class, JpaAuditConfig.class, QuoteSheetValidationExtremePostgresTest.StubConfig.class})
class QuoteSheetValidationExtremePostgresTest {

    private static final long SHEET_ID = 940000000000000101L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 9, 16);
    private static final BigDecimal LENGTH_PREMIUM = new BigDecimal("30");

    @Autowired
    private QuoteSheetStore store;

    @Autowired
    private QuoteSheetRepository repository;

    @AfterEach
    void cleanup() {
        if (repository.existsById(SHEET_ID)) {
            repository.deleteById(SHEET_ID);
        }
    }

    /** 行号空洞 {2,3} 整单替换 2 行: 归一化为 {1,2}, 复用原实体, 不触发唯一键。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lineNoHoleStartingAtTwo_wholeReplace_renumbersWithoutUniqueViolation() {
        persistSheetWithItems(2, 3);
        long version = dbVersion();

        QuoteSheetResponse response = store.update(SHEET_ID,
                wholeRequest(false, item("螺纹钢", "HRB400E", 12, "10", "3600"),
                        item("盘螺", "HRB400E", 8, "5", "3700")),
                version);

        assertThat(response.items()).extracting(QuoteSheetResponse.ItemResponse::lineNo).containsExactly(1, 2);
        // 存储层直调时响应版本落后(父 FORCE_INCREMENT 在提交阶段应用); 以 DB 权威版本为准。
        assertThat(dbVersion()).isEqualTo(version + 1);
        assertThat(dbLineNos()).containsExactly(1, 2);
    }

    /** 行号空洞 {1,3} 整单替换 2 行: 归一化为 {1,2}, 不触发唯一键。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lineNoHoleWithGap_wholeReplace_renumbersWithoutUniqueViolation() {
        persistSheetWithItems(1, 3);
        long version = dbVersion();

        QuoteSheetResponse response = store.update(SHEET_ID,
                wholeRequest(false, item("螺纹钢", "HRB400E", 12, "10", "3600"),
                        item("盘螺", "HRB400E", 8, "5", "3700")),
                version);

        assertThat(response.items()).extracting(QuoteSheetResponse.ItemResponse::lineNo).containsExactly(1, 2);
        assertThat(dbLineNos()).containsExactly(1, 2);
    }

    /** 行号重排: 请求顺序决定归一化后的行号, 原实体按位置保留, 不产生唯一键冲突。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lineNoReorder_wholeReplace_followsRequestOrderWithoutUniqueViolation() {
        persistSheetWithItems(1, 2, 3);
        long version = dbVersion();

        QuoteSheetResponse response = store.update(SHEET_ID,
                wholeRequest(false, item("高线", "HPB300", 10, "1", "3800"),
                        item("螺纹钢", "HRB400E", 12, "10", "3600"),
                        item("盘螺", "HRB400E", 8, "5", "3700")),
                version);

        assertThat(response.items()).extracting(QuoteSheetResponse.ItemResponse::lineNo).containsExactly(1, 2, 3);
        assertThat(response.items()).extracting(QuoteSheetResponse.ItemResponse::category)
                .containsExactly("高线", "螺纹钢", "盘螺");
        assertThat(dbLineNos()).containsExactly(1, 2, 3);
    }

    /** trim 后同名品牌在入库前 422, 版本不推进。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicateBrandAfterTrim_isRejected422AndLeavesVersionUnchanged() {
        persistSheetWithItems(1);
        long version = dbVersion();

        assertThatThrownBy(() -> store.update(SHEET_ID,
                new QuoteSheetRequest("pg-validate", null, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                        LENGTH_PREMIUM, false, false, "报价", null,
                        List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.ZERO, 0),
                                new QuoteSheetRequest.BrandRequest(" 中天 ", BigDecimal.ZERO, 1)),
                        List.of(item("螺纹钢", "HRB400E", 12, "10", "3600"))),
                version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌重复")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(dbVersion()).isEqualTo(version);
    }

    /** 同一行 prices trim 后重名 422, 不落 uk_quote_item_price 409。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicatePriceBrandAfterTrim_isRejected422() {
        persistSheetWithItems(1);
        long version = dbVersion();

        QuoteSheetRequest.ItemRequest duplicatedPrices =
                new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3600"), null),
                                new QuoteSheetRequest.ItemPriceRequest(" 中天 ", new BigDecimal("3610"), null)));
        assertThatThrownBy(() -> store.update(SHEET_ID, wholeRequest(false, duplicatedPrices), version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌重复")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(dbVersion()).isEqualTo(version);
    }

    /** 大小写不同视为不同品牌(PG 唯一约束区分大小写): 允许落库为两行, 不误判 422/409。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void brandNamesDifferingOnlyByCase_arePersistedAsDistinctRows() {
        persistSheetWithItems(1);
        long version = dbVersion();

        QuoteSheetResponse response = store.update(SHEET_ID,
                new QuoteSheetRequest("pg-validate", null, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                        LENGTH_PREMIUM, false, false, "报价", null,
                        List.of(new QuoteSheetRequest.BrandRequest("ABC", BigDecimal.ZERO, 0),
                                new QuoteSheetRequest.BrandRequest("abc", BigDecimal.ZERO, 1)),
                        List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                                List.of(new QuoteSheetRequest.ItemPriceRequest("ABC", new BigDecimal("3600"), null))))),
                version);

        assertThat(response.brands()).extracting(QuoteSheetResponse.BrandResponse::brandName)
                .containsExactlyInAnyOrder("ABC", "abc");
        assertThat(dbVersion()).isEqualTo(version + 1);
    }

    /**
     * 超长品牌名(65 > varchar(64))在存储层触发 {@link DataIntegrityViolationException};
     * 其根因消息不含 duplicate/unique, 由全局异常映射为 422(字段长度越界)而非 409。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void overlongBrandName_isDataIntegrityViolationThatMapsTo422NotConflict() {
        String overlong = "中".repeat(65);

        QuoteSheetRequest.ItemRequest overlongItem = new QuoteSheetRequest.ItemRequest(
                "螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                List.of(new QuoteSheetRequest.ItemPriceRequest(overlong, new BigDecimal("3600"), null)));
        QuoteSheetRequest request = new QuoteSheetRequest(
                "pg-validate", null, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                LENGTH_PREMIUM, false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest(overlong, BigDecimal.ZERO, 0)),
                List.of(overlongItem));

        assertThatThrownBy(() -> store.create(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    String root = String.valueOf(((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage());
                    assertThat(root).doesNotContain("duplicate key").doesNotContain("unique constraint");
                    assertThat(root).containsIgnoringCase("too long");
                });
    }

    /**
     * 同一请求内显式解锁 + 改规格: 当前实现先校验后应用表头, 因此判 422 并整体回滚(锁与版本不变)。
     * 见任务报告对"单请求解锁+改规格"语义的记录。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sameRequestUnlockAndSpecChange_isRejectedAndRolledBack() {
        persistSheetWithItems(true, 1);
        long version = dbVersion();

        assertThatThrownBy(() -> store.update(SHEET_ID,
                wholeRequest(false, item("螺纹钢", "HRB400E", 14, "10", "3600")), version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        QuoteSheet reloaded = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow();
        assertThat(reloaded.isSpecQuantityLocked()).isTrue();
        assertThat(reloaded.getVersion()).isEqualTo(version);
    }

    private long dbVersion() {
        return repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();
    }

    private List<Integer> dbLineNos() {
        return repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getItems().stream()
                .map(QuoteSheetItem::getLineNo)
                .sorted()
                .toList();
    }

    private void persistSheetWithItems(int... lineNos) {
        persistSheetWithItems(false, lineNos);
    }

    private void persistSheetWithItems(boolean specQuantityLocked, int... lineNos) {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setSheetNo("PG-VALID-" + SHEET_ID);
        sheet.setName("pg-validate");
        sheet.setProjectName("云潮筝鸣府");
        sheet.setOrderDate(ORDER_DATE);
        sheet.setRefDate(ORDER_DATE);
        sheet.setRefPeriod("09:00");
        sheet.setLengthPremium(LENGTH_PREMIUM);
        sheet.setStatus("报价");
        sheet.setSpecQuantityLocked(specQuantityLocked);
        sheet.setCreatedAt(LocalDateTime.now());
        sheet.setCreatedBy(0L);
        sheet.setCreatedName("system");

        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(SHEET_ID + 1);
        brand.setSheet(sheet);
        brand.setBrandName("中天");
        brand.setFreight(BigDecimal.ZERO);
        brand.setSortOrder(0);
        sheet.getBrands().add(brand);

        long itemId = SHEET_ID + 10;
        for (int lineNo : lineNos) {
            QuoteSheetItem item = new QuoteSheetItem();
            item.setId(itemId);
            item.setSheet(sheet);
            item.setLineNo(lineNo);
            item.setCategory("螺纹钢");
            item.setMaterial("HRB400E");
            item.setSpec(12);
            item.setLength("9米");
            item.setTon(new BigDecimal("10"));
            QuoteSheetItemPrice price = new QuoteSheetItemPrice();
            price.setId(itemId + 100);
            price.setItem(item);
            price.setBrandName("中天");
            price.setSpotPrice(new BigDecimal("3500"));
            item.getPrices().add(price);
            sheet.getItems().add(item);
            itemId += 1;
        }
        repository.saveAndFlush(sheet);
    }

    private QuoteSheetRequest wholeRequest(boolean specQuantityLocked,
                                           QuoteSheetRequest.ItemRequest... items) {
        return new QuoteSheetRequest(
                "pg-validate", null, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                LENGTH_PREMIUM, false, specQuantityLocked, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.ZERO, 0)),
                List.of(items));
    }

    private QuoteSheetRequest.ItemRequest item(String category, String material, int spec,
                                               String ton, String spotPrice) {
        return new QuoteSheetRequest.ItemRequest(category, material, spec, "9米", new BigDecimal(ton),
                List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal(spotPrice), null)));
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
