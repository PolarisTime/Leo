package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 缺陷 B 的真实 PostgreSQL 回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>
 * 整单替换分支的规格数量锁校验必须取"请求中的显式值"门控:
 * <ul>
 *   <li>显式 {@code specQuantityLocked=false} + 改规格/数量: 同一 PUT 内 200 且落库解锁+新值;</li>
 *   <li>不传该字段(null): 仍按持久化旧值(锁定)拒绝 422;</li>
 *   <li>显式 {@code true}: 拒绝 422;</li>
 *   <li>仅显式解锁、规格/数量不变: 200 且落库解锁。</li>
 * </ul>
 * 以 {@code NOT_SUPPORTED} 关闭测试托管事务让每次写真实提交; 每条用例结束按主键清理。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({QuoteSheetStore.class, QuoteSheetUnlockSpecQuantityReplacePostgresTest.StubConfig.class})
class QuoteSheetUnlockSpecQuantityReplacePostgresTest {

    private static final long SHEET_UNLOCK = 940000000000000101L;
    private static final long SHEET_OMITTED = 940000000000000111L;
    private static final long SHEET_LOCKED_TRUE = 940000000000000121L;
    private static final long SHEET_UNLOCK_ONLY = 940000000000000131L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 9, 16);
    private static final BigDecimal LENGTH_PREMIUM = new BigDecimal("30");

    @Autowired
    private QuoteSheetStore store;

    @Autowired
    private QuoteSheetRepository repository;

    @AfterEach
    void cleanup() {
        for (long sheetId : new long[]{SHEET_UNLOCK, SHEET_OMITTED, SHEET_LOCKED_TRUE, SHEET_UNLOCK_ONLY}) {
            if (repository.existsById(sheetId)) {
                repository.deleteById(sheetId);
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void explicitUnlockWithSpecAndQuantityChangeInSamePut_succeedsAndPersists() {
        persistLockedSheet(SHEET_UNLOCK);

        QuoteSheetResponse response =
                store.update(SHEET_UNLOCK, fullRequest(10, BigDecimal.ONE, "3280", false), null);

        assertThat(response.specQuantityLocked()).isFalse();
        assertThat(response.items().get(0).spec()).isEqualTo(10);
        assertThat(response.items().get(0).ton()).isEqualByComparingTo("1");

        QuoteSheet persisted = repository.findByIdAndDeletedFlagFalse(SHEET_UNLOCK).orElseThrow();
        assertThat(persisted.isSpecQuantityLocked()).isFalse();
        assertThat(persisted.getItems().get(0).getSpec()).isEqualTo(10);
        assertThat(persisted.getItems().get(0).getTon()).isEqualByComparingTo("1");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void omittedSpecQuantityLocked_stillRejectsSpecChange() {
        persistLockedSheet(SHEET_OMITTED);

        QuoteSheetRequest omitted = new QuoteSheetRequest(
                "pg-unlock-spec", null, null, ORDER_DATE, ORDER_DATE, "09:00",
                LENGTH_PREMIUM, false, null, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 10, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));

        assertThatThrownBy(() -> store.update(SHEET_OMITTED, omitted, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        QuoteSheet persisted = repository.findByIdAndDeletedFlagFalse(SHEET_OMITTED).orElseThrow();
        assertThat(persisted.getItems().get(0).getSpec()).isEqualTo(12);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void explicitLockTrue_rejectsSpecChange() {
        persistLockedSheet(SHEET_LOCKED_TRUE);

        assertThatThrownBy(() ->
                store.update(SHEET_LOCKED_TRUE, fullRequest(10, BigDecimal.TEN, "3280", true), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        QuoteSheet persisted = repository.findByIdAndDeletedFlagFalse(SHEET_LOCKED_TRUE).orElseThrow();
        assertThat(persisted.getItems().get(0).getSpec()).isEqualTo(12);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void explicitUnlockWithoutSpecChange_releasesLock() {
        persistLockedSheet(SHEET_UNLOCK_ONLY);

        QuoteSheetResponse response =
                store.update(SHEET_UNLOCK_ONLY, fullRequest(12, BigDecimal.TEN, "3280", false), null);

        assertThat(response.specQuantityLocked()).isFalse();
        assertThat(response.items().get(0).spec()).isEqualTo(12);
        assertThat(repository.findByIdAndDeletedFlagFalse(SHEET_UNLOCK_ONLY).orElseThrow().isSpecQuantityLocked())
                .isFalse();
    }

    private void persistLockedSheet(long sheetId) {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(sheetId);
        sheet.setSheetNo("PG-UNLOCK-SPEC-" + sheetId);
        sheet.setName("pg-unlock-spec");
        sheet.setOrderDate(ORDER_DATE);
        sheet.setRefDate(ORDER_DATE);
        sheet.setRefPeriod("09:00");
        sheet.setLengthPremium(LENGTH_PREMIUM);
        sheet.setSpecQuantityLocked(true);
        sheet.setStatus("报价");
        sheet.setCreatedAt(LocalDateTime.now());
        sheet.setCreatedBy(0L);
        sheet.setCreatedName("system");

        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(sheetId + 1);
        brand.setSheet(sheet);
        brand.setBrandName("中天");
        brand.setFreight(new BigDecimal("30"));
        brand.setSortOrder(0);
        sheet.getBrands().add(brand);

        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(sheetId + 2);
        item.setSheet(sheet);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setTon(BigDecimal.TEN);
        QuoteSheetItemPrice price = new QuoteSheetItemPrice();
        price.setId(sheetId + 3);
        price.setItem(item);
        price.setBrandName("中天");
        price.setSpotPrice(new BigDecimal("3500"));
        item.getPrices().add(price);
        sheet.getItems().add(item);

        repository.saveAndFlush(sheet);
    }

    private QuoteSheetRequest fullRequest(Integer spec, BigDecimal ton, String spotPrice,
                                          boolean specQuantityLocked) {
        return new QuoteSheetRequest(
                "pg-unlock-spec", null, null, ORDER_DATE, ORDER_DATE, "09:00",
                LENGTH_PREMIUM, false, specQuantityLocked, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", spec, "9米", ton,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal(spotPrice), null)))));
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
