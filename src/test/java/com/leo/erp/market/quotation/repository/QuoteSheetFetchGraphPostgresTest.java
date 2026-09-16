package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 回归验证(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>
 * 插入 1 张含品牌、明细与现货价的报价单, 调用 {@link QuoteSheetRepository#findByIdAndDeletedFlagFalse(Long)}
 * 与行级写路径(父单据 {@code OPTIMISTIC_FORCE_INCREMENT}), 断言不再抛
 * {@code MultipleBagFetchException}, 且 LAZY 的 {@code brands}/{@code items.prices}
 * 能在同一事务内完整初始化。连接信息复用 {@code application.yml}({@code ../.env.local})。
 * <p>
 * @DataJpaTest 默认事务回滚, 不会污染数据库; ddl-auto/flyway 均关闭, 仅对既有 schema 读写。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class QuoteSheetFetchGraphPostgresTest {

    @Autowired
    private QuoteSheetRepository repository;

    @Autowired
    private TestEntityManager em;

    @Test
    void findByIdAndDeletedFlagFalse_andRowWrite_doNotThrowMultipleBagFetch() {
        long id = 900000000000000001L;
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(id);
        sheet.setSheetNo("PG-" + id);
        sheet.setName("pg-fetch-graph");
        sheet.setOrderDate(LocalDate.now());
        sheet.setRefDate(LocalDate.now());
        sheet.setRefPeriod("2026-09");
        sheet.setLengthPremium(new BigDecimal("30"));
        sheet.setCreatedAt(LocalDateTime.now());
        sheet.setCreatedBy(0L);
        sheet.setCreatedName("system");

        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(id + 1);
        brand.setSheet(sheet);
        brand.setBrandName("HRB400E");
        brand.setFreight(BigDecimal.ZERO);
        brand.setSortOrder(0);
        sheet.getBrands().add(brand);

        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(id + 2);
        item.setSheet(sheet);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(20);
        item.setLength("12米");
        item.setTon(BigDecimal.ONE);
        QuoteSheetItemPrice price = new QuoteSheetItemPrice();
        price.setId(id + 3);
        price.setItem(item);
        price.setBrandName("HRB400E");
        price.setSpotPrice(new BigDecimal("3500"));
        item.getPrices().add(price);
        sheet.getItems().add(item);

        repository.saveAndFlush(sheet);
        em.clear();

        QuoteSheet loaded = repository.findByIdAndDeletedFlagFalse(id).orElseThrow();

        assertThat(loaded.getItems()).hasSize(1);
        assertThat(loaded.getItems().get(0).getPrices()).hasSize(1);
        assertThat(loaded.getBrands()).hasSize(1);

        // 行级写路径: 加父单据 OPTIMISTIC_FORCE_INCREMENT 锁后只改子集合并 flush, 不得抛异常。
        // (@DataJpaTest 事务回滚, 强制版本自增在提交时生效, 故此处不校验版本数值。)
        em.getEntityManager().lock(loaded, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        loaded.getItems().get(0).getPrices().get(0).setSpotPrice(new BigDecimal("3600"));
        em.flush();
        em.clear();

        QuoteSheet reloaded = repository.findByIdAndDeletedFlagFalse(id).orElseThrow();
        assertThat(reloaded.getItems().get(0).getPrices().get(0).getSpotPrice())
                .isEqualByComparingTo("3600");
    }
}
