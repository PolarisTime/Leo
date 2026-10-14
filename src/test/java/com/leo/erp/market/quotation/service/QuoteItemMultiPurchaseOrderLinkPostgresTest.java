package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery.PurchaseOrderItemOptionSnapshot;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery.PurchaseOrderOptionSnapshot;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 一行关联多张采购订单明细行的真实库回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>覆盖: 多行关联的存取与顺序、旧三列镜像第一条、汇总查询按「旧单值列 ∪ 新关联表」去重
 * (同一 (明细行, 订单明细行) 关联只计一次), 以及同一行吨位对每张已关联明细行都计入一次。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({QuoteSheetStore.class, QuoteItemMultiPurchaseOrderLinkPostgresTest.StubConfig.class})
class QuoteItemMultiPurchaseOrderLinkPostgresTest {

    private static final long SHEET_ID = 910000000000000201L;
    private static final long ITEM_ID = SHEET_ID + 2;
    private static final long PO_1 = 920000000000000001L;
    private static final long PO_2 = 920000000000000002L;
    private static final long POI_1 = 921000000000000001L;
    private static final long POI_2 = 921000000000000002L;

    @Autowired
    private QuoteSheetStore store;

    @Autowired
    private QuoteSheetRepository repository;

    @Autowired
    private PurchaseOrderOptionQuery purchaseOrderOptionQuery;

    @AfterEach
    void cleanup() {
        if (repository.existsById(SHEET_ID)) {
            repository.deleteById(SHEET_ID);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoLinksPerRow_persistOrderMirrorFirstAndCountTonOncePerLink() {
        stubPurchaseOrders();
        persistSheet();
        Long version = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();

        // 130 吨的行拆到两张明细行: 60 + 70
        QuoteSheetResponse response = store.update(SHEET_ID, request(List.of(
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_1, POI_1, new BigDecimal("60")),
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_2, POI_2, new BigDecimal("70"))
        )), version);

        List<QuoteSheetResponse.ItemPurchaseOrderResponse> links = response.items().get(0).purchaseOrderLinks();
        assertThat(links).hasSize(2);
        assertThat(links.get(0).purchaseOrderItemId()).isEqualTo(POI_1);
        assertThat(links.get(0).allocatedTon()).isEqualByComparingTo("60");
        assertThat(links.get(1).purchaseOrderItemId()).isEqualTo(POI_2);
        assertThat(links.get(1).allocatedTon()).isEqualByComparingTo("70");
        // 旧三列镜像第一条, 历史读取口径不变
        assertThat(response.items().get(0).purchaseOrderId()).isEqualTo(PO_1);
        assertThat(response.items().get(0).purchaseOrderItemId()).isEqualTo(POI_1);

        // 重新读回(新事务): 关联集合与顺序都必须落库
        QuoteSheetResponse reloaded = store.detail(SHEET_ID);
        assertThat(reloaded.items().get(0).purchaseOrderLinks())
                .extracting(QuoteSheetResponse.ItemPurchaseOrderResponse::purchaseOrderItemId)
                .containsExactly(POI_1, POI_2);

        // 汇总按**本次在该明细行开出的吨位**(allocated_ton)计: 60 + 70 = 报单 130
        assertThat(tonByItem(POI_1, null)).isEqualByComparingTo("60");
        assertThat(tonByItem(POI_2, null)).isEqualByComparingTo("70");
        assertThat(links.get(0).allocatedTon().add(links.get(1).allocatedTon()))
                .isEqualByComparingTo("130");
        // 排除本单据后没有其他占用: 查询不返回该明细行(调用方按 null 当 0 处理)
        assertThat(tonByItem(POI_1, SHEET_ID)).isNull();
        // 未关联的明细行不参与行级口径
        assertThat(tonByItem(921000000000000099L, null)).isNull();
        // 有分配行时旧单值列不再重复计(旧列只是第一条的镜像)
        assertThat(tonByItem(POI_1, null)).isEqualByComparingTo("60");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void legacySingleLinkRequest_isSynthesizedIntoOneLink() {
        stubPurchaseOrders();
        persistSheet();
        Long version = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();

        // 旧形态请求: 只带 purchaseOrderId/purchaseOrderItemId, 不带列表
        QuoteSheetResponse response = store.update(SHEET_ID, legacyRequest(PO_2, POI_2), version);

        List<QuoteSheetResponse.ItemPurchaseOrderResponse> links = response.items().get(0).purchaseOrderLinks();
        assertThat(links).hasSize(1);
        assertThat(links.get(0).purchaseOrderItemId()).isEqualTo(POI_2);
        // 分配吨位取本行吨位
        assertThat(links.get(0).allocatedTon()).isEqualByComparingTo("130");
    }

    /** 与服务端口径一致: 关联分配量 + 历史旧列兜底, 两段相加。 */
    private BigDecimal tonByItem(long purchaseOrderItemId, Long excludeSheetId) {
        List<Long> ids = List.of(purchaseOrderItemId);
        BigDecimal allocated = repository
                .sumAllocatedTonByPurchaseOrderItemIds(ids, excludeSheetId).stream()
                .findFirst().map(row -> (BigDecimal) row[1]).orElse(BigDecimal.ZERO);
        BigDecimal legacy = repository
                .sumLegacyTonByPurchaseOrderItemIds(ids, excludeSheetId).stream()
                .findFirst().map(row -> (BigDecimal) row[1]).orElse(BigDecimal.ZERO);
        BigDecimal total = allocated.add(legacy);
        return total.signum() == 0 && allocated.signum() == 0 && legacy.signum() == 0
                ? null : total;
    }

    private void stubPurchaseOrders() {
        when(purchaseOrderOptionQuery.listActiveByIds(any())).thenReturn(List.of(
                new PurchaseOrderOptionSnapshot(PO_1, "PO-1", "沙钢", new BigDecimal("400"), "正常",
                        LocalDateTime.now()),
                new PurchaseOrderOptionSnapshot(PO_2, "PO-2", "沙钢", new BigDecimal("400"), "正常",
                        LocalDateTime.now())
        ));
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any())).thenReturn(List.of(
                new PurchaseOrderItemOptionSnapshot(PO_1, POI_1, "PO-1", "沙钢", "螺纹钢", "HRB400E", "12",
                        "9米", "中天", new BigDecimal("200"), "正常"),
                new PurchaseOrderItemOptionSnapshot(PO_2, POI_2, "PO-2", "沙钢", "螺纹钢", "HRB400E", "12",
                        "9米", "中天", new BigDecimal("200"), "正常")
        ));
    }

    private void persistSheet() {
        LocalDate today = LocalDate.now();
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setSheetNo("PG-LINK-" + SHEET_ID);
        sheet.setName("pg-multi-link");
        sheet.setOrderDate(today);
        sheet.setRefDate(today);
        sheet.setRefPeriod("09:00");
        sheet.setLengthPremium(new BigDecimal("30"));
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
        item.setId(ITEM_ID);
        item.setSheet(sheet);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setTon(new BigDecimal("130"));
        item.setLocked(true);
        sheet.getItems().add(item);

        repository.saveAndFlush(sheet);
    }

    private QuoteSheetRequest request(List<QuoteSheetRequest.ItemPurchaseOrderRequest> links) {
        LocalDate today = LocalDate.now();
        return new QuoteSheetRequest(
                "pg-multi-link", null, null, today, today, "09:00",
                new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.ZERO, 0)),
                List.of(new QuoteSheetRequest.ItemRequest(null, "螺纹钢", "HRB400E", 12, "9米", null,
                        new BigDecimal("130"), true, null, null, links, null)));
    }

    private QuoteSheetRequest legacyRequest(long purchaseOrderId, long purchaseOrderItemId) {
        LocalDate today = LocalDate.now();
        return new QuoteSheetRequest(
                "pg-multi-link", null, null, today, today, "09:00",
                new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.ZERO, 0)),
                List.of(new QuoteSheetRequest.ItemRequest(null, "螺纹钢", "HRB400E", 12, "9米", null,
                        new BigDecimal("130"), true, purchaseOrderId, purchaseOrderItemId, null, null)));
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
            return new SnowflakeIdGenerator();
        }
    }
}
