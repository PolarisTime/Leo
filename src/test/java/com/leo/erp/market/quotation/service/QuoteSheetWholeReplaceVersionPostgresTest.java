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
 * 真实 PostgreSQL 回归验证(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>
 * 验证 P1-1: 整体替换(brands/items 任一非 null)在仅改动子集合(某现货价)、表头不变时,
 * 父单据 {@code @Version} 仍必须 {@code OPTIMISTIC_FORCE_INCREMENT} 恰好 +1;
 * 用旧版本再次整体 PUT 必须按 412(PRECONDITION_FAILED)失败。
 * <p>
 * 版本自增由 Hibernate 的 {@code EntityIncrementVersionProcess} 在事务提交前应用,
 * 因此本用例以 {@code NOT_SUPPORTED} 关闭测试托管事务, 让每次写真实提交后再回读版本;
 * 每条用例结束按主键清理, 不污染数据库。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({QuoteSheetStore.class, QuoteSheetWholeReplaceVersionPostgresTest.StubConfig.class})
class QuoteSheetWholeReplaceVersionPostgresTest {

    private static final long SHEET_ID = 910000000000000101L;

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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void wholeReplace_withChildOnlyChange_incrementsVersionExactlyOnceThenStaleWriteReturns412() {
        LocalDate orderDate = LocalDate.now();
        String refPeriod = "09:00";
        BigDecimal lengthPremium = new BigDecimal("30");

        persistSheet(orderDate, refPeriod, lengthPremium);
        Long versionBefore = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();

        // 表头完全不变, 仅把某现货价 3500 -> 3600(纯子集合变更), 响应与提交后的实体版本都必须 +1。
        QuoteSheetResponse updated =
                store.update(SHEET_ID, request(orderDate, refPeriod, lengthPremium, "3600"), versionBefore);
        assertThat(updated.items().get(0).prices().get(0).spotPrice()).isEqualByComparingTo("3600");
        assertThat(repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion())
                .isEqualTo(versionBefore + 1);

        // 表头-only 更新由 @Version 自然递增一次, 不得双增。
        QuoteSheetResponse headerOnly = store.update(SHEET_ID,
                new QuoteSheetRequest("pg-whole-replace", null, null, orderDate, orderDate, refPeriod,
                        lengthPremium, false, false, "报价", "仅改备注", null, null),
                versionBefore + 1);
        assertThat(headerOnly.remark()).isEqualTo("仅改备注");
        assertThat(repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion())
                .isEqualTo(versionBefore + 2);

        // 另一条"并发"用旧版本再整体 PUT: 版本已变更, 必须 412 而非静默覆盖。
        Long staleVersion = versionBefore;
        assertThatThrownBy(() -> store.update(SHEET_ID, request(orderDate, refPeriod, lengthPremium, "3700"), staleVersion))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本已变更")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRECONDITION_FAILED);
    }

    private void persistSheet(LocalDate orderDate, String refPeriod, BigDecimal lengthPremium) {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setSheetNo("PG-WR-" + SHEET_ID);
        sheet.setName("pg-whole-replace");
        sheet.setOrderDate(orderDate);
        sheet.setRefDate(orderDate);
        sheet.setRefPeriod(refPeriod);
        sheet.setLengthPremium(lengthPremium);
        sheet.setStatus("报价");
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

        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(SHEET_ID + 2);
        item.setSheet(sheet);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setTon(BigDecimal.ONE);
        QuoteSheetItemPrice price = new QuoteSheetItemPrice();
        price.setId(SHEET_ID + 3);
        price.setItem(item);
        price.setBrandName("中天");
        price.setSpotPrice(new BigDecimal("3500"));
        item.getPrices().add(price);
        sheet.getItems().add(item);

        repository.saveAndFlush(sheet);
    }

    private QuoteSheetRequest request(LocalDate orderDate, String refPeriod, BigDecimal lengthPremium, String spotPrice) {
        return new QuoteSheetRequest(
                "pg-whole-replace", null, null, orderDate, orderDate, refPeriod,
                lengthPremium, false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.ZERO, 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.ONE,
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
            return Mockito.mock(SnowflakeIdGenerator.class);
        }
    }
}
