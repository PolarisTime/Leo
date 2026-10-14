package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.PurchaseOrderTonnageResponse;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery.PurchaseOrderItemOptionSnapshot;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery.PurchaseOrderOptionSnapshot;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 一行关联多张采购订单明细行的真实库回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>覆盖: 多行关联的存取与顺序、旧三列镜像第一条、汇总查询按「旧单值列 ∪ 新关联表」去重
 * (同一 (明细行, 订单明细行) 关联只计一次)、同一行吨位对每张已关联明细行都计入一次、
 * 旧形态单值请求不得静默截断多行关联(P2-17), 以及汇总只发一条 SQL 的单快照保证(P2-18)。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        // 单快照用例靠它统计该读路径实际发出的 SQL 条数
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.leo.erp.market.quotation.service.QuoteItemMultiPurchaseOrderLinkPostgresTest$SqlCollector"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({QuoteSheetStore.class, PurchaseOrderTonnageService.class,
        QuoteItemMultiPurchaseOrderLinkPostgresTest.StubConfig.class})
class QuoteItemMultiPurchaseOrderLinkPostgresTest {

    private static final long SHEET_ID = 910000000000000201L;
    private static final long ITEM_ID = SHEET_ID + 2;
    private static final long PO_1 = 920000000000000001L;
    private static final long PO_2 = 920000000000000002L;
    private static final long POI_1 = 921000000000000001L;
    private static final long POI_2 = 921000000000000002L;
    private static final long POI_3 = 921000000000000003L;

    @Autowired
    private QuoteSheetStore store;

    @Autowired
    private QuoteSheetRepository repository;

    @Autowired
    private PurchaseOrderTonnageService tonnageService;

    @Autowired
    private PurchaseOrderOptionQuery purchaseOrderOptionQuery;

    @BeforeEach
    void resetSqlCollector() {
        SqlCollector.reset();
    }

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

    /** 分配不变量: ΣallocatedTon 必须等于本行吨位, 不等时 422 且整笔回滚, 不得静默落库。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void mismatchedAllocationSum_isRejectedAndNothingPersisted() {
        stubPurchaseOrders();
        persistSheet();
        Long version = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();

        // 130 吨的行只分配 60 吨: Σ != 本行吨位, 必须明确报错并说明缺口
        assertThatThrownBy(() -> store.update(SHEET_ID, request(List.of(
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_1, POI_1, new BigDecimal("60")))), version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致")
                .hasMessageContaining("还缺 70 吨");

        // 事务回滚: 关联表与旧三列都不得残留
        QuoteSheetResponse reloaded = store.detail(SHEET_ID);
        assertThat(reloaded.items().get(0).purchaseOrderLinks()).isEmpty();
        assertThat(reloaded.items().get(0).purchaseOrderId()).isNull();
    }

    /**
     * P2-17 核心回归(真实库): 已有 3 条关联的行收到旧形态单值请求(purchaseOrderLinks 缺失,
     * 只带 purchaseOrderId/purchaseOrderItemId)时必须 422, 绝不静默压成一条。
     * <p>旧实现会合成一条 allocated_ton = 本行吨位(130)的关联并 clear + addAll, 3 条变 1 条,
     * 而 Σ 分配吨位校验恰好通过(Σ(130) == 130), 数据丢失无人察觉。</p>
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void legacySingleLinkRequest_onThreeLinkedRow_isRejectedAndLinksKept() {
        stubPurchaseOrders();
        persistSheet();
        Long version = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();

        store.update(SHEET_ID, request(List.of(
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_1, POI_1, new BigDecimal("40")),
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_2, POI_2, new BigDecimal("40")),
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_1, POI_3, new BigDecimal("50"))
        )), version);
        Long linkedVersion = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();

        // 旧形态: 只带单值三列, 没有 purchaseOrderLinks
        assertThatThrownBy(() -> store.update(SHEET_ID,
                legacyRequest(PO_2, POI_2), linkedVersion))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单值形态")
                .hasMessageContaining("purchaseOrderLinks");

        // 3 条关联必须原样保留(顺序与分配吨位都不变), 没有被截断
        QuoteSheetResponse reloaded = store.detail(SHEET_ID);
        List<QuoteSheetResponse.ItemPurchaseOrderResponse> keptLinks =
                reloaded.items().get(0).purchaseOrderLinks();
        assertThat(keptLinks)
                .extracting(QuoteSheetResponse.ItemPurchaseOrderResponse::purchaseOrderItemId)
                .containsExactly(POI_1, POI_2, POI_3);
        // numeric(18,8) 回读标度不同, 按数值比较
        assertThat(keptLinks.get(0).allocatedTon()).isEqualByComparingTo("40");
        assertThat(keptLinks.get(1).allocatedTon()).isEqualByComparingTo("40");
        assertThat(keptLinks.get(2).allocatedTon()).isEqualByComparingTo("50");
        assertThat(reloaded.items().get(0).purchaseOrderId()).isEqualTo(PO_1);
    }

    /**
     * P2-17: 未携带任何采购订单字段(旧单值三列也为 null)的整行请求 = “不改关联”,
     * 3 条关联原样保留; 清空必须显式传空数组。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void absentLinkFields_keepExistingLinks_whileEmptyArrayClearsThem() {
        stubPurchaseOrders();
        persistSheet();
        Long version = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();

        store.update(SHEET_ID, request(List.of(
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_1, POI_1, new BigDecimal("60")),
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_2, POI_2, new BigDecimal("70"))
        )), version);
        Long linkedVersion = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();

        // 未携带: 2 条关联保留
        QuoteSheetResponse kept = store.update(SHEET_ID, requestWithoutLinks(), linkedVersion);
        assertThat(kept.items().get(0).purchaseOrderLinks()).hasSize(2);
        assertThat(kept.items().get(0).purchaseOrderItemId()).isEqualTo(POI_1);

        // 显式空数组: 清空关联与旧三列
        Long keptVersion = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();
        QuoteSheetResponse cleared = store.update(SHEET_ID, request(List.of()), keptVersion);
        assertThat(cleared.items().get(0).purchaseOrderLinks()).isEmpty();
        assertThat(cleared.items().get(0).purchaseOrderId()).isNull();
        assertThat(cleared.items().get(0).purchaseOrderItemId()).isNull();
        assertThat(cleared.items().get(0).purchased()).isFalse();
        assertThat(store.detail(SHEET_ID).items().get(0).purchaseOrderLinks()).isEmpty();
    }

    /**
     * P2-18 回归(真实库): 汇总口径的两段(关联 allocated_ton + 历史旧列 ton)必须由**一条** SQL
     * 读完, 由数据库保证单快照 —— 拆成两条语句时, 另一事务在两句之间提交(旧单值行改写成关联行)
     * 会让该行两段都不计而瞬时少计。
     * <p>并发时序难以稳定复现, 这里以 Hibernate {@link StatementInspector} 统计该读路径实际发出的
     * SQL: 只允许 1 条 select。</p>
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void summarize_issuesExactlyOneSelectForSingleSnapshot() {
        stubPurchaseOrders();
        persistSheet();
        Long version = repository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();
        store.update(SHEET_ID, request(List.of(
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_1, POI_1, new BigDecimal("60")),
                new QuoteSheetRequest.ItemPurchaseOrderRequest(PO_2, POI_2, new BigDecimal("70"))
        )), version);

        SqlCollector.reset();
        List<PurchaseOrderTonnageResponse> summary = tonnageService.summarize(List.of(POI_1, POI_2), null);

        // 明细行选项查询是 mock(按 stub 全量返回), 这里只关心已开吨位口径
        Map<Long, BigDecimal> issuedById = summary.stream().collect(Collectors.toMap(
                PurchaseOrderTonnageResponse::purchaseOrderItemId,
                PurchaseOrderTonnageResponse::issuedWeight));
        assertThat(issuedById.get(POI_1)).isEqualByComparingTo("60");
        assertThat(issuedById.get(POI_2)).isEqualByComparingTo("70");
        assertThat(SqlCollector.selects())
                .as("两段口径必须在同一条 select(单快照)里读完, 实际发出: " + SqlCollector.selects())
                .hasSize(1);
    }

    /** 与服务端口径一致: 关联分配量 + 历史旧列兜底两段由同一条聚合 SQL 给出(单快照), 单测里求和。 */
    private BigDecimal tonByItem(long purchaseOrderItemId, Long excludeSheetId) {
        List<Long> ids = List.of(purchaseOrderItemId);
        List<Object[]> rows = repository.sumIssuedTonByPurchaseOrderItemIds(ids, excludeSheetId);
        BigDecimal total = rows.stream()
                .findFirst()
                .map(row -> row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1])
                .orElse(BigDecimal.ZERO);
        return total.signum() == 0 ? null : total;
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
                        "9米", "中天", new BigDecimal("200"), "正常"),
                new PurchaseOrderItemOptionSnapshot(PO_1, POI_3, "PO-1", "沙钢", "螺纹钢", "HRB400E", "12",
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

    /** 整行请求: 联接字段全部不携带(null), 即"请求里没有这个字段"。 */
    private QuoteSheetRequest requestWithoutLinks() {
        LocalDate today = LocalDate.now();
        return new QuoteSheetRequest(
                "pg-multi-link", null, null, today, today, "09:00",
                new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.ZERO, 0)),
                List.of(new QuoteSheetRequest.ItemRequest(null, "螺纹钢", "HRB400E", 12, "9米", null,
                        new BigDecimal("130"), true, null, null, null, null)));
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

    /**
     * 统计 Hibernate 实际发出的 SQL(Hibernate 通过
     * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector} 按类名实例化,
     * 因此必须是 public static 且有无参构造)。
     */
    public static class SqlCollector implements StatementInspector {

        private static final ThreadLocal<List<String>> STATEMENTS = ThreadLocal.withInitial(ArrayList::new);

        static void reset() {
            STATEMENTS.get().clear();
        }

        /** 本次调用发出的 select 语句(已 trim); 只关心汇总读路径, 不看别的 DML。 */
        static List<String> selects() {
            return STATEMENTS.get().stream()
                    .filter(sql -> sql.stripLeading().toLowerCase().startsWith("select"))
                    .toList();
        }

        @Override
        public String inspect(String sql) {
            STATEMENTS.get().add(sql);
            return sql;
        }
    }
}
