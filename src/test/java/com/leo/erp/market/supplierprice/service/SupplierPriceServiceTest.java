package com.leo.erp.market.supplierprice.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.supplierprice.domain.entity.SupplierPriceItem;
import com.leo.erp.market.supplierprice.domain.entity.SupplierPriceList;
import com.leo.erp.market.supplierprice.repository.SupplierPriceListRepository;
import com.leo.erp.market.supplierprice.web.dto.SupplierPriceGridResponse;
import com.leo.erp.market.supplierprice.web.dto.SupplierPriceListRequests;
import com.leo.erp.master.api.SupplierQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商价格表（比价一手价）的核心口径。
 *
 * <p>覆盖三件最容易悄悄坏掉的事：① 默认取<b>最低价</b>且同价时结果稳定；② 一个品牌同一供应商
 * 不能重复接入；③ 保存格子必须带版本前置条件，否则后保存的人会静默覆盖前一个人刚录入的价。</p>
 */
class SupplierPriceServiceTest {

    private final SupplierPriceListRepository repository = mock(SupplierPriceListRepository.class);
    private final SupplierQuery supplierQuery = mock(SupplierQuery.class);
    private final SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
    private final SupplierPriceService service =
            new SupplierPriceService(repository, supplierQuery, idGenerator);

    private static SupplierPriceList list(String supplierName, String price) {
        SupplierPriceList list = new SupplierPriceList();
        list.setId(1L);
        list.setSupplierId(11L);
        list.setSupplierName(supplierName);
        list.setBrandName("中天");
        list.setQuotedOn(LocalDate.of(2026, 10, 22));
        list.setVersion(3L);
        SupplierPriceItem item = new SupplierPriceItem();
        item.setId(101L);
        item.setList(list);
        item.setCategory("直条");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setPrice(new BigDecimal(price));
        list.getItems().add(item);
        return list;
    }

    @Test
    /** 默认取最低价；同价时按供应商名取第一个，保证结果不随查询顺序漂移。 */
    void findLowestQuote_picksLowestPriceAndStaysStableOnTie() {
        when(repository.findQuotedItemsByProduct("中天", "直条", "HRB400E", 12, "9米"))
                .thenReturn(List.of(
                        list("浙江钧叶", "3410").getItems().get(0),
                        list("物产中大", "3390").getItems().get(0),
                        list("浙江企坤", "3430").getItems().get(0)));

        var quote = service.findLowestQuote("中天", "直条", "HRB400E", 12, "9米");

        assertThat(quote).isPresent();
        assertThat(quote.get().price()).isEqualByComparingTo("3390");
        assertThat(quote.get().supplierName()).isEqualTo("物产中大");
    }

    @Test
    /** 批量取价带上供应商简称：报单比价的「简称」列要简称，不是几十字的长全称。 */
    void lowestQuotes_includesSupplierShortNameFromMasterData() {
        when(repository.findQuotedItems(null)).thenReturn(List.of(
                list("浙江企坤集团有限公司（融诚支付）", "3390").getItems().get(0)));
        when(supplierQuery.findActiveById(11L)).thenReturn(Optional.of(
                new SupplierQuery.SupplierSnapshot(
                        11L, "SUP-011", "浙江企坤集团有限公司（融诚支付）", "企坤融诚")));

        var quotes = service.lowestQuotes(null);

        assertThat(quotes).hasSize(1);
        assertThat(quotes.get(0).supplierShortName()).isEqualTo("企坤融诚");
        // 全称仍原样返回：前端 tooltip / 列 title 需要它做兜底与可读性
        assertThat(quotes.get(0).supplierName()).isEqualTo("浙江企坤集团有限公司（融诚支付）");
    }

    @Test
    /** 主数据缺失或简称为空时返回 null，让前端回退全称，不显示空白。 */
    void lowestQuotes_supplierShortNameIsNullWhenMasterDataMissing() {
        when(repository.findQuotedItems(null)).thenReturn(List.of(
                list("浙江铁都钢材有限公司", "3410").getItems().get(0)));
        when(supplierQuery.findActiveById(11L)).thenReturn(Optional.empty());

        var quotes = service.lowestQuotes(null);

        assertThat(quotes).hasSize(1);
        assertThat(quotes.get(0).supplierShortName()).isNull();
        assertThat(quotes.get(0).supplierName()).isEqualTo("浙江铁都钢材有限公司");
    }

    @Test
    /** 一条报价都没有时返回空，而不是 0 元（不报价 ≠ 0）。 */
    void findLowestQuote_returnsEmptyWhenNobodyQuoted() {
        when(repository.findQuotedItemsByProduct(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.findLowestQuote("中天", "直条", "HRB400E", 12, "9米")).isEmpty();
    }

    @Test
    /** 同品牌同供应商重复接入返回 409，且不写库（库里有唯一索引兜底，这里先探测给可读文案）。 */
    void addColumn_rejectsDuplicateSupplierInSameBrand() {
        when(supplierQuery.findActiveNormalById(11L)).thenReturn(Optional.of(
                new SupplierQuery.SupplierSnapshot(11L, "SUP-011", "物产中大", "物产中大")));
        when(repository.findBySupplierIdAndBrandNameAndDeletedFlagFalse(11L, "中天"))
                .thenReturn(Optional.of(list("物产中大", "3390")));

        assertThatThrownBy(() -> service.addColumn(new SupplierPriceListRequests.AddColumn(
                "中天", 11L, LocalDate.of(2026, 10, 22), null, null)))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    /** 版本不符返回 409：后保存的人不得静默覆盖他人刚录入的价。 */
    void saveItems_rejectsStaleVersion() {
        SupplierPriceList existing = list("物产中大", "3390");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.saveItems(1L, new SupplierPriceListRequests.SaveItems(
                99L, List.of(new SupplierPriceListRequests.Item(
                        "直条", "HRB400E", 12, "9米", new BigDecimal("3398"), null, null)))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    /** 单价为空的格子不落条目，因此"清空一个格子的价"能真正落库。 */
    void saveItems_dropsEmptyCellsSoClearingPersists() {
        SupplierPriceList existing = list("物产中大", "3390");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        SupplierPriceGridResponse.Column column = service.saveItems(1L,
                new SupplierPriceListRequests.SaveItems(3L, List.of()));

        assertThat(existing.getItems()).isEmpty();
        assertThat(column.listId()).isEqualTo(1L);
    }

    @Test
    /**
     * 同规格再次保存走「就地更新」，不新增条目。
     *
     * <p>回归：之前整组 clear + 重新插入，Hibernate 会先 INSERT 再 DELETE，撞上
     * {@code uk_supplier_price_item_key} 唯一索引，被映射成 409（真机复现：同一列第二次保存
     * 起稳定 409）。就地更新后旧条目 id 不变，只有新增规格才会插入。</p>
     */
    void saveItems_updatesExistingItemsInPlace() {
        SupplierPriceList existing = list("物产中大", "3390");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        service.saveItems(1L, new SupplierPriceListRequests.SaveItems(3L, List.of(
                new SupplierPriceListRequests.Item("直条", "HRB400E", 12, "9米",
                        new BigDecimal("3398"), null, null),
                new SupplierPriceListRequests.Item("直条", "HRB400E", 12, "12米",
                        new BigDecimal("3410"), null, null))));

        assertThat(existing.getItems()).hasSize(2);
        assertThat(existing.getItems())
                .filteredOn(item -> "9米".equals(item.getLength()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getId()).isEqualTo(101L);
                    assertThat(item.getPrice()).isEqualByComparingTo("3398");
                });
        assertThat(existing.getItems())
                .filteredOn(item -> "12米".equals(item.getLength()))
                .hasSize(1);
    }

    @Test
    /**
     * 保存成功后必须返回 flush 后的新版本号。
     *
     * <p>回归：之前只调 {@code repository.save(list)} 就返回，JPA 的 {@code @Version} 要到 flush
     * 才自增，响应里的 version 仍是旧值；前端自动保存拿旧版本再提交一次就会 409
     * （真机复现：第一次 PUT 返回 version=0，第二次 PUT expectedVersion=0 被 409 拒绝）。</p>
     */
    void saveItems_returnsVersionAfterFlush() {
        SupplierPriceList existing = list("物产中大", "3390");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenAnswer(invocation -> {
            existing.setVersion(existing.getVersion() + 1);
            return existing;
        });

        SupplierPriceGridResponse.Column column = service.saveItems(1L,
                new SupplierPriceListRequests.SaveItems(3L, List.of()));

        assertThat(column.version()).isEqualTo(4L);
    }
}
