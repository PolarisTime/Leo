package com.leo.erp.market.quotation.service;

import com.leo.erp.common.config.ClockConfig;
import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 回归验证(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>
 * P0-1: {@code OPTIMISTIC_FORCE_INCREMENT} 在事务提交阶段才应用, 存储层 flush 后构造的 DTO 版本会落后 1。
 * 服务层在写事务提交后以新事务回读版本, 本用例断言: 行级写、整单替换、项目配置保存后,
 * 响应/返回版本 == 数据库回读版本, 且每次写恰好 +1(含多次连续写)。
 * <p>
 * P1-2: 覆盖"表头变 / 表头不变仅子集合变 / 表头-only"路径, 断言父版本任意路径恰好 +1, 不发生 +2。
 * <p>
 * 以 {@code NOT_SUPPORTED} 关闭测试托管事务, 让每次写真实提交; 每条用例结束按主键清理。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({QuoteSheetStore.class, QuoteSheetService.class, QuoteSheetEditLockService.class,
        QuoteProjectConfigStore.class, QuoteProjectConfigService.class, JpaAuditConfig.class,
        ClockConfig.class, QuoteVersionPostgresTest.StubConfig.class})
class QuoteVersionPostgresTest {

    private static final long SHEET_ID = 920000000000000101L;
    private static final long ITEM_ID = 920000000000000102L;
    private static final long BRAND_ID = 920000000000000103L;
    private static final long PRICE_ID = 920000000000000104L;
    private static final long PROJECT_ID = 920000000000000301L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 9, 16);
    private static final BigDecimal LENGTH_PREMIUM = new BigDecimal("30");

    @Autowired
    private QuoteSheetService sheetService;

    @Autowired
    private QuoteProjectConfigService configService;

    @Autowired
    private QuoteSheetRepository sheetRepository;

    @Autowired
    private QuoteProjectConfigRepository configRepository;

    @AfterEach
    void cleanup() {
        if (sheetRepository.existsById(SHEET_ID)) {
            sheetRepository.deleteById(SHEET_ID);
        }
        configRepository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID)
                .map(QuoteProjectConfig::getId)
                .ifPresent(configRepository::deleteById);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rowLevelWrites_responseVersionEqualsCommittedDbVersionAndIncrementsOnce() {
        persistSheet();
        long version = sheetDbVersion();
        assertThat(version).isEqualTo(0L);

        QuoteSheetItemWrite added = sheetService.addItem(SHEET_ID,
                itemRequest("盘螺", "HRB400E", 8, "3300"), version, 0L);
        assertThat(added.version()).isEqualTo(version + 1).isEqualTo(sheetDbVersion());

        QuoteSheetItemWrite updated = sheetService.updateItem(SHEET_ID, added.item().id(),
                itemRequest("盘螺", "HRB400E", 8, "3400"), added.version(), 0L);
        assertThat(updated.version()).isEqualTo(version + 2).isEqualTo(sheetDbVersion());

        Long deleted = sheetService.deleteItem(SHEET_ID, added.item().id(), updated.version(), 0L);
        assertThat(deleted).isEqualTo(version + 3).isEqualTo(sheetDbVersion());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void fullReplaceAndHeaderOnly_incrementExactlyOnceInEveryHeaderCase() {
        persistSheet();
        long version = sheetDbVersion();

        // 1) 仅子集合变更(表头完全不变) -> FORCE_INCREMENT, +1
        QuoteSheetResponse childOnly = sheetService.update(SHEET_ID, wholeRequest(null, "3600"), version, 0L);
        assertThat(childOnly.version()).isEqualTo(version + 1).isEqualTo(sheetDbVersion());

        // 2) 表头-only 更新 -> 自然 @Version, +1
        QuoteSheetResponse headerOnly = sheetService.update(SHEET_ID,
                new QuoteSheetRequest("pg-version", null, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                        LENGTH_PREMIUM, false, false, "报价", "备注一", null, null),
                childOnly.version(), 0L);
        assertThat(headerOnly.version()).isEqualTo(version + 2).isEqualTo(sheetDbVersion());

        // 3) 表头标量与子集合同时变更 -> 仅自然 @Version, 恰好 +1(不得 +2)
        QuoteSheetResponse headerAndChild =
                sheetService.update(SHEET_ID, wholeRequest("备注二", "3700"), headerOnly.version(), 0L);
        assertThat(headerAndChild.version()).isEqualTo(version + 3).isEqualTo(sheetDbVersion());

        // 4) 表头不再变化、仅再次改现货价 -> FORCE_INCREMENT, 恰好 +1
        QuoteSheetResponse childOnlyAgain =
                sheetService.update(SHEET_ID, wholeRequest("备注二", "3800"), headerAndChild.version(), 0L);
        assertThat(childOnlyAgain.version()).isEqualTo(version + 4).isEqualTo(sheetDbVersion());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void projectConfigSave_responseVersionEqualsCommittedDbVersionAndIncrementsOnce() {
        QuoteProjectConfigResponse created = configService.save(PROJECT_ID, configRequest(LENGTH_PREMIUM), null);
        assertThat(created.version()).isEqualTo(0L).isEqualTo(configDbVersion());

        // 标量不变、仅改品牌子集合 -> FORCE_INCREMENT, +1
        QuoteProjectConfigResponse brandOnly = configService.save(PROJECT_ID,
                configBrandRequest(LENGTH_PREMIUM, "28"), created.version());
        assertThat(brandOnly.version()).isEqualTo(created.version() + 1).isEqualTo(configDbVersion());

        // 标量变更 -> 自然 @Version, 恰好 +1
        QuoteProjectConfigResponse scalarChanged = configService.save(PROJECT_ID,
                configRequest(new BigDecimal("40")), brandOnly.version());
        assertThat(scalarChanged.version()).isEqualTo(brandOnly.version() + 1).isEqualTo(configDbVersion());
    }

    private long sheetDbVersion() {
        return sheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();
    }

    private long configDbVersion() {
        return configRepository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID).orElseThrow().getVersion();
    }

    private void persistSheet() {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setSheetNo("PG-VER-" + SHEET_ID);
        sheet.setName("pg-version");
        sheet.setProjectName("云潮筝鸣府");
        sheet.setOrderDate(ORDER_DATE);
        sheet.setRefDate(ORDER_DATE);
        sheet.setRefPeriod("09:00");
        sheet.setLengthPremium(LENGTH_PREMIUM);
        sheet.setStatus("报价");
        sheet.setCreatedAt(LocalDateTime.now());
        sheet.setCreatedBy(0L);
        sheet.setCreatedName("system");

        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(BRAND_ID);
        brand.setSheet(sheet);
        brand.setBrandName("中天");
        brand.setFreight(new BigDecimal("30"));
        brand.setSortOrder(0);
        sheet.getBrands().add(brand);

        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(ITEM_ID);
        item.setSheet(sheet);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setTon(BigDecimal.ONE);
        QuoteSheetItemPrice price = new QuoteSheetItemPrice();
        price.setId(PRICE_ID);
        price.setItem(item);
        price.setBrandName("中天");
        price.setSpotPrice(new BigDecimal("3500"));
        item.getPrices().add(price);
        sheet.getItems().add(item);

        sheetRepository.saveAndFlush(sheet);
    }

    private QuoteSheetRequest wholeRequest(String remark, String spotPrice) {
        return new QuoteSheetRequest(
                "pg-version", null, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                LENGTH_PREMIUM, false, false, "报价", remark,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal(spotPrice), null)))));
    }

    private QuoteSheetRequest.ItemRequest itemRequest(String category, String material, int spec, String spotPrice) {
        return new QuoteSheetRequest.ItemRequest(category, material, spec, "9米", BigDecimal.ONE,
                List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal(spotPrice), null)));
    }

    private QuoteProjectConfigRequest configRequest(BigDecimal lengthPremium) {
        return new QuoteProjectConfigRequest(
                lengthPremium, false,
                List.of("螺纹钢|HRB400E|12|9米"), List.of("中天"), "项目备注",
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("30"),
                        List.of("螺纹钢"), 0)));
    }

    private QuoteProjectConfigRequest configBrandRequest(BigDecimal lengthPremium, String brandFreight) {
        return new QuoteProjectConfigRequest(
                lengthPremium, false,
                List.of("螺纹钢|HRB400E|12|9米"), List.of("中天"), "项目备注",
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal(brandFreight),
                        List.of("螺纹钢"), 0)));
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
