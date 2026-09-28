package com.leo.erp.market.quotation.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import com.leo.erp.market.pricelist.service.QuoteSheetPriceDeriver;
import com.leo.erp.market.pricelist.service.QuoteSheetPriceService;
import com.leo.erp.market.pricelist.service.SpotReason;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.domain.enums.QuoteRowType;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.SupplierQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 比价单读取的价格格生成(核心验收点): 价格格必须按「该单据品牌列 × 该行」生成,
 * 且现货价<b>只</b>来自当前供应商价格表。
 *
 * <p>覆盖: 无落库行时也能带出 {@code PRICE_LIST} 价与供应商; 三种未命中原因仍出格({@code NONE});
 * 落库手填值<b>不影响</b>读结果(手填覆盖已删除); 价格表改价后立即跟随; 多品牌顺序稳定无重复;
 * 隔断行不生成价格格。</p>
 */
@ExtendWith(MockitoExtension.class)
class QuoteSheetPriceCellProjectionTest {

    private static final long SHEET_ID = 9007199254740993L;
    private static final long ITEM_ID = 500L;
    private static final long REBAR_SUPPLIER_ID = 777L;
    private static final long WIRE_SUPPLIER_ID = 888L;

    @Mock
    private QuoteSheetRepository quoteSheetRepository;

    @Mock
    private QuoteSheetPriceDeriver deriver;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private com.leo.erp.market.pricelist.repository.SupplierPriceListRepository priceListRepository;

    @Mock
    private com.leo.erp.market.pricelist.repository.SupplierPriceItemRepository priceItemRepository;

    /**
     * 未装配价格推导服务时读取不得抛异常: 仍按品牌列出格, 但无价格表价可用时
     * {@code NONE}(推导原因不可知, 保持 null)。
     */
    @Test
    void withoutPriceService_stillEmitsOneCellPerBrand() {
        QuoteSheet sheet = sheetWith(List.of("安徽富鑫"), List.of(item(ITEM_ID, "盘螺", "HRB400", 6, "-")));
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
        QuoteSheetStore storeWithoutPriceService = new QuoteSheetStore(quoteSheetRepository, null,
                snowflakeIdGenerator, supplierQuery, null, null, null);

        QuoteSheetResponse response = storeWithoutPriceService.detail(SHEET_ID);

        assertThat(response.items().get(0).prices()).hasSize(1);
        assertThat(response.items().get(0).prices().get(0).brandName()).isEqualTo("安徽富鑫");
        assertThat(response.items().get(0).prices().get(0).spotPrice()).isNull();
        assertThat(response.items().get(0).prices().get(0).spotSource()).isEqualTo("NONE");
    }

    /** 主路径: 有品牌、价格表有对应条目 → 每行每品牌都有格子且带出价与供应商。 */
    @Test
    void derivesPriceForEveryBrandFromPriceList() {
        QuoteSheet sheet = sheetWith(List.of("安徽富鑫"), List.of(item(ITEM_ID, "盘螺", "HRB400", 6, "-")));
        when(deriver.selectBrands(any(), any())).thenReturn(selection(Map.of(
                "安徽富鑫", entry(300L, REBAR_SUPPLIER_ID, "杭州中金钢铁", "3500.00",
                        "盘螺", "HRB400", 6, "-"))));
        QuoteSheetResponse.ItemResponse row = detail(sheet).items().get(0);

        assertThat(row.prices()).hasSize(1);
        QuoteSheetResponse.ItemPriceResponse cell = row.prices().get(0);
        assertThat(cell.brandName()).isEqualTo("安徽富鑫");
        assertThat(cell.spotPrice()).isEqualByComparingTo("3500.00");
        assertThat(cell.derivedSpotPrice()).isEqualByComparingTo("3500.00");
        assertThat(cell.spotSource()).isEqualTo("PRICE_LIST");
        assertThat(cell.spotReason()).isNull();
        assertThat(cell.supplierId()).isEqualTo(REBAR_SUPPLIER_ID);
        assertThat(cell.supplierName()).isEqualTo("杭州中金钢铁");
        assertThat(cell.priceListId()).isEqualTo(300L);
        assertThat(cell.id()).isNull();
        assertThat(cell.priceSource()).isNull();
    }

    /** 未命中时仍然出格, 并且带对应原因枚举。 */
    @Test
    void unmatchedCells_stillAppearWithReason() {
        QuoteSheet sheet = sheetWith(List.of("安徽富鑫", "武钢汉钢"),
                List.of(item(ITEM_ID, "螺纹钢", "HRB400E", 12, "9米")));
        when(deriver.selectBrands(any(), any())).thenReturn(selection(Map.of(
                "安徽富鑫", new QuoteSheetPriceDeriver.BrandEntry(null, Map.of()),
                "武钢汉钢", entry(300L, WIRE_SUPPLIER_ID, "武钢", null, "螺纹钢", "HRB400E", 12, "9米"))));
        List<QuoteSheetResponse.ItemPriceResponse> cells = detail(sheet).items().get(0).prices();

        assertThat(cells).hasSize(2);
        assertThat(cells.get(0).spotPrice()).isNull();
        assertThat(cells.get(0).spotSource()).isEqualTo("NONE");
        assertThat(cells.get(0).spotReason()).isEqualTo(SpotReason.NO_LIST.name());
        assertThat(cells.get(1).spotPrice()).isNull();
        assertThat(cells.get(1).spotSource()).isEqualTo("NONE");
        assertThat(cells.get(1).spotReason()).isEqualTo(SpotReason.NO_PRICE.name());
    }

    /**
     * 关键回归: 落库的手填现货价(历史 {@code mk_quote_item_price} 行)<b>不得</b>影响读结果,
     * 现货价必须以当前价格表为准。
     */
    @Test
    void storedManualRow_doesNotAffectDerivedPrice() {
        QuoteSheet sheet = sheetWith(List.of("安徽富鑫"), List.of(item(ITEM_ID, "盘螺", "HRB400", 6, "-")));
        stored(900L, sheet.getItems().get(0), "安徽富鑫", "3400.00");
        when(deriver.selectBrands(any(), any())).thenReturn(selection(Map.of(
                "安徽富鑫", entry(300L, REBAR_SUPPLIER_ID, "杭州中金钢铁", "3500.00",
                        "盘螺", "HRB400", 6, "-"))));

        QuoteSheetResponse.ItemPriceResponse cell = detail(sheet).items().get(0).prices().get(0);

        assertThat(cell.spotPrice()).isEqualByComparingTo("3500.00");
        assertThat(cell.spotSource()).isEqualTo("PRICE_LIST");
        assertThat(cell.derivedSpotPrice()).isEqualByComparingTo("3500.00");
        assertThat(cell.supplierName()).isEqualTo("杭州中金钢铁");
        // 落库行ID/来源快照都不再对外透出
        assertThat(cell.id()).isNull();
        assertThat(cell.priceSource()).isNull();
    }

    /** 落库有价但价格表无价: 不得回退快照, 一律 {@code NONE} + 原因。 */
    @Test
    void storedRowIsIgnoredWhenPriceListHasNoPrice() {
        QuoteSheet sheet = sheetWith(List.of("安徽富鑫"), List.of(item(ITEM_ID, "盘螺", "HRB400", 6, "-")));
        stored(900L, sheet.getItems().get(0), "安徽富鑫", "3400.00");
        when(deriver.selectBrands(any(), any())).thenReturn(selection(Map.of(
                "安徽富鑫", new QuoteSheetPriceDeriver.BrandEntry(null, Map.of()))));

        QuoteSheetResponse.ItemPriceResponse cell = detail(sheet).items().get(0).prices().get(0);

        assertThat(cell.spotPrice()).isNull();
        assertThat(cell.spotSource()).isEqualTo("NONE");
        assertThat(cell.spotReason()).isEqualTo(SpotReason.NO_LIST.name());
    }

    /** 多品牌: 格数与品牌数一致, 顺序按 sortOrder 稳定且无重复。 */
    @Test
    void multipleBrands_produceOneCellPerBrandInSortOrder() {
        QuoteSheet sheet = sheetWith(List.of("乙品牌", "甲品牌"),
                List.of(item(ITEM_ID, "螺纹钢", "HRB400E", 12, "9米")));
        when(deriver.selectBrands(any(), any())).thenReturn(selection(Map.of(
                "乙品牌", entry(301L, REBAR_SUPPLIER_ID, "供应商乙", "3200.00", "螺纹钢", "HRB400E", 12, "9米"),
                "甲品牌", entry(302L, WIRE_SUPPLIER_ID, "供应商甲", "3300.00", "螺纹钢", "HRB400E", 12, "9米"))));
        List<QuoteSheetResponse.ItemPriceResponse> cells = detail(sheet).items().get(0).prices();

        // sortOrder: 乙品牌=0, 甲品牌=1
        assertThat(cells).extracting(QuoteSheetResponse.ItemPriceResponse::brandName)
                .containsExactly("乙品牌", "甲品牌");
        assertThat(cells).extracting(QuoteSheetResponse.ItemPriceResponse::spotPrice)
                .containsExactly(new BigDecimal("3200.00"), new BigDecimal("3300.00"));
    }

    /** 隔断行不生成价格格。 */
    @Test
    void separatorRow_hasNoPriceCells() {
        QuoteSheet sheet = sheetWith(List.of("安徽富鑫"), List.of(separator(ITEM_ID)));
        when(deriver.selectBrands(any(), any())).thenReturn(selection(Map.of(
                "安徽富鑫", entry(300L, REBAR_SUPPLIER_ID, "杭州中金钢铁", "3500.00", null, null, null, null))));
        assertThat(detail(sheet).items().get(0).prices()).isEmpty();
    }

    /**
     * 取消版本语义的核心验收: 用<b>真实</b>推导链读单据, 价格表改价后单据现货价必须立即跟着变
     * (直接取当前价格表价, 没有历史快照; 历史单据要钉住价只能靠手填覆盖)。
     */
    @Test
    void currentPriceFollowsPriceListChangeImmediately() {
        QuoteSheet sheet = sheetWith(List.of("安徽富鑫"), List.of(item(ITEM_ID, "盘螺", "HRB400", 6, "-")));
        stubCurrentList("3500.00");

        QuoteSheetStore store = storeWithRealDeriver();
        QuoteSheetResponse.ItemPriceResponse before = store.detail(SHEET_ID).items().get(0).prices().get(0);
        assertThat(before.spotPrice()).isEqualByComparingTo("3500.00");
        assertThat(before.spotReason()).isNull();

        // 价格表调价: 同一张表改条目价并前移 updated_at
        stubCurrentList("3600.00");

        QuoteSheetResponse.ItemPriceResponse after = store.detail(SHEET_ID).items().get(0).prices().get(0);
        assertThat(after.spotPrice()).isEqualByComparingTo("3600.00");
        assertThat(after.spotSource()).isEqualTo("PRICE_LIST");
    }

    // ---------------------------------------------------------------- 夹具

    /** 用真实 QuoteSheetPriceDeriver(走仓储)装配读路径。 */
    private QuoteSheetStore storeWithRealDeriver() {
        com.leo.erp.market.pricelist.service.QuoteSheetPriceDeriver realDeriver =
                new com.leo.erp.market.pricelist.service.QuoteSheetPriceDeriver(
                        new com.leo.erp.market.pricelist.service.SupplierPriceListQueryService(
                                priceListRepository, priceItemRepository, null));
        QuoteSheetPriceService priceService = new QuoteSheetPriceService(realDeriver, quoteSheetRepository);
        QuoteSheetStore store = new QuoteSheetStore(quoteSheetRepository, null, snowflakeIdGenerator,
                supplierQuery, null, null, null);
        store.setPriceService(priceService);
        return store;
    }

    private void stubCurrentList(String price) {
        SupplierPriceList list = new SupplierPriceList();
        list.setId(300L);
        list.setSupplierId(REBAR_SUPPLIER_ID);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName("安徽富鑫");
        list.setUpdatedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        list.setStatus(SupplierPriceList.STATUS_ACTIVE);
        SupplierPriceItem entry = new SupplierPriceItem();
        entry.setId(301L);
        entry.setList(list);
        entry.setCategory("盘螺");
        entry.setMaterial("HRB400");
        entry.setSpec(6);
        entry.setLength("-");
        entry.setPrice(new BigDecimal(price));
        when(priceListRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(priceItemRepository.findByListIdIn(any())).thenReturn(List.of(entry));
        QuoteSheet sheet = sheetWith(List.of("安徽富鑫"), List.of(item(ITEM_ID, "盘螺", "HRB400", 6, "-")));
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
    }

    private QuoteSheetStore store() {
        QuoteSheetPriceService priceService = new QuoteSheetPriceService(deriver, quoteSheetRepository);
        QuoteSheetStore store = new QuoteSheetStore(quoteSheetRepository, null, snowflakeIdGenerator,
                supplierQuery, null, null, null);
        store.setPriceService(priceService);
        return store;
    }

    /** 走真实 detail 读路径(与前端 GET /quote-sheets/{id} 一致)。 */
    private QuoteSheetResponse detail(QuoteSheet sheet) {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
        return store().detail(SHEET_ID);
    }

    private static QuoteSheet sheetWith(List<String> brandNames, List<QuoteSheetItem> items) {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setSheetNo("9001");
        sheet.setName("比价格式单");
        sheet.setOrderDate(LocalDate.of(2026, 9, 28));
        sheet.setRefDate(LocalDate.of(2026, 9, 28));
        sheet.setRefPeriod("09:30");
        sheet.setLengthPremium(new BigDecimal("30"));
        sheet.setStatus("报价");
        sheet.setVersion(0L);
        List<QuoteSheetBrand> brands = new ArrayList<>();
        int sortOrder = 0;
        for (String brandName : brandNames) {
            QuoteSheetBrand brand = new QuoteSheetBrand();
            brand.setId(1000L + sortOrder);
            brand.setSheet(sheet);
            brand.setBrandName(brandName);
            brand.setFreight(new BigDecimal("60.00"));
            brand.setSortOrder(sortOrder);
            brands.add(brand);
            sortOrder++;
        }
        sheet.setBrands(brands);
        for (QuoteSheetItem item : items) {
            item.setSheet(sheet);
        }
        sheet.setItems(new ArrayList<>(items));
        return sheet;
    }

    private static QuoteSheetItem item(Long id, String category, String material, Integer spec, String length) {
        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(id);
        item.setLineNo(1);
        item.setRowType(QuoteRowType.PRODUCT);
        item.setCategory(category);
        item.setMaterial(material);
        item.setSpec(spec);
        item.setLength(length);
        item.setPrices(new ArrayList<>());
        return item;
    }

    private static QuoteSheetItem separator(Long id) {
        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(id);
        item.setLineNo(1);
        item.setRowType(QuoteRowType.SEPARATOR);
        item.setPrices(new ArrayList<>());
        return item;
    }

    /** 造一条历史落库现货价行(读路径必须完全忽略它)。 */
    private static QuoteSheetItemPrice stored(Long id, QuoteSheetItem item, String brandName, String price) {
        QuoteSheetItemPrice row = new QuoteSheetItemPrice();
        row.setId(id);
        row.setItem(item);
        row.setBrandName(brandName);
        row.setSpotPrice(new BigDecimal(price));
        row.setPriceSource(QuoteSheetItemPrice.SOURCE_MANUAL);
        item.getPrices().add(row);
        return row;
    }

    private static QuoteSheetPriceDeriver.BrandSelection selection(
            Map<String, QuoteSheetPriceDeriver.BrandEntry> byBrand) {
        return new QuoteSheetPriceDeriver.BrandSelection(new LinkedHashMap<>(byBrand));
    }

    private static QuoteSheetPriceDeriver.BrandEntry entry(Long listId, Long supplierId, String supplierName,
                                                           String price, String category, String material,
                                                           Integer spec, String length) {
        SupplierPriceList list = new SupplierPriceList();
        list.setId(listId);
        list.setSupplierId(supplierId);
        list.setSupplierName(supplierName);
        list.setBrandName("品牌");
        list.setReleasedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        list.setStatus(SupplierPriceList.STATUS_ACTIVE);
        if (category == null) {
            return new QuoteSheetPriceDeriver.BrandEntry(list, Map.of());
        }
        SupplierPriceItem item = new SupplierPriceItem();
        item.setId(listId + 1);
        item.setList(list);
        item.setCategory(category);
        item.setMaterial(material);
        item.setSpec(spec);
        item.setLength(length);
        item.setPrice(price == null ? null : new BigDecimal(price));
        Map<String, SupplierPriceItem> items = new LinkedHashMap<>();
        items.put(item.keyOf(), item);
        return new QuoteSheetPriceDeriver.BrandEntry(list, items);
    }
}
