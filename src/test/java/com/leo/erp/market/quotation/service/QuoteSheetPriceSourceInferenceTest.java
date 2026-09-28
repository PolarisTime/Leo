package com.leo.erp.market.quotation.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import com.leo.erp.market.pricelist.service.QuoteSheetPriceDeriver;
import com.leo.erp.market.pricelist.service.QuoteSheetPriceService;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteSheetItemPriceRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
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
 * 批量保存路径的价格来源推断(后端先上线时的防御): 提交价等于价格表推导价时落库
 * {@code price_source=PRICE_LIST} 并继续跟随价格表调价, 不得静默冻结为人工覆盖。
 */
@ExtendWith(MockitoExtension.class)
class QuoteSheetPriceSourceInferenceTest {

    private static final long SHEET_ID = 9007199254740993L;
    private static final long ITEM_ID = 500L;
    private static final long SUPPLIER_ID = 777L;
    private static final String BRAND = "安徽富鑫";

    @Mock
    private QuoteSheetRepository quoteSheetRepository;

    @Mock
    private QuoteSheetItemPriceRepository itemPriceRepository;

    @Mock
    private QuoteSheetPriceDeriver deriver;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    private QuoteSheetStore store() {
        QuoteSheetPriceService priceService = new QuoteSheetPriceService(deriver, quoteSheetRepository,
                itemPriceRepository, snowflakeIdGenerator, supplierQuery);
        QuoteSheetStore store = new QuoteSheetStore(quoteSheetRepository, null, snowflakeIdGenerator,
                supplierQuery, null, null, entityManager);
        store.setPriceService(priceService);
        return store;
    }

    /** 提交价 == 推导价 → PRICE_LIST, 且记录来源版本与发布时刻。 */
    @Test
    void submittedPriceEqualToDerived_isStoredAsPriceListWithSourceSnapshot() {
        QuoteSheet sheet = sheetWith(null);
        stubSheetAndDerivation(sheet, "3500.00");
        when(snowflakeIdGenerator.nextId()).thenReturn(7777L);

        store().updateItem(SHEET_ID, ITEM_ID, request("3500.00"), null);

        QuoteSheetItemPrice row = sheet.getItems().get(0).getPrices().get(0);
        assertThat(row.getPriceSource()).isEqualTo(QuoteSheetItemPrice.SOURCE_PRICE_LIST);
        assertThat(row.getSpotPrice()).isEqualByComparingTo("3500.00");
        assertThat(row.getPriceListId()).isEqualTo(300L);
        assertThat(row.getPriceListReleasedAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 8, 0));
    }

    /** 提交价 != 推导价 → MANUAL(人工填写), 不记录来源版本。 */
    @Test
    void submittedPriceDifferentFromDerived_isStoredAsManual() {
        QuoteSheet sheet = sheetWith(null);
        stubSheetAndDerivation(sheet, "3500.00");
        when(snowflakeIdGenerator.nextId()).thenReturn(7777L);

        store().updateItem(SHEET_ID, ITEM_ID, request("3400.00"), null);

        QuoteSheetItemPrice row = sheet.getItems().get(0).getPrices().get(0);
        assertThat(row.getPriceSource()).isEqualTo(QuoteSheetItemPrice.SOURCE_MANUAL);
        assertThat(row.getSpotPrice()).isEqualByComparingTo("3400.00");
        assertThat(row.getPriceListId()).isNull();
        assertThat(row.getPriceListReleasedAt()).isNull();
    }

    /** 无可比推导(无生效版本/无条目/不报价) → MANUAL。 */
    @Test
    void submittedPriceWithoutDerivation_isStoredAsManual() {
        QuoteSheet sheet = sheetWith(null);
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(null, null));

        store().updateItem(SHEET_ID, ITEM_ID, request("3500.00"), null);

        assertThat(sheet.getItems().get(0).getPrices().get(0).getPriceSource())
                .isEqualTo(QuoteSheetItemPrice.SOURCE_MANUAL);
    }

    /** 重复提交相同值: 不新建行、不覆盖既有来源(避免无意义写入)。 */
    @Test
    void unchangedSubmittedValue_keepsExistingSourceAndRow() {
        QuoteSheetItemPrice existing = new QuoteSheetItemPrice();
        existing.setId(900L);
        existing.setBrandName(BRAND);
        existing.setSpotPrice(new BigDecimal("3500.00"));
        existing.setPriceSource(QuoteSheetItemPrice.SOURCE_MANUAL);
        QuoteSheet sheet = sheetWith(existing);
        stubSheetAndDerivation(sheet, "3500.00");

        store().updateItem(SHEET_ID, ITEM_ID, request("3500.00"), null);

        assertThat(sheet.getItems().get(0).getPrices()).hasSize(1);
        QuoteSheetItemPrice row = sheet.getItems().get(0).getPrices().get(0);
        assertThat(row.getId()).isEqualTo(900L);
        // 值未变化 → 保留人工钉住的 MANUAL, 不因"恰好等于推导价"被改写
        assertThat(row.getPriceSource()).isEqualTo(QuoteSheetItemPrice.SOURCE_MANUAL);
    }

    /** 覆盖行改为与推导价相同的新值 → 转为 PRICE_LIST(值变了才重新推断)。 */
    @Test
    void changedSubmittedValueMatchingDerived_becomesPriceList() {
        QuoteSheetItemPrice existing = new QuoteSheetItemPrice();
        existing.setId(900L);
        existing.setBrandName(BRAND);
        existing.setSpotPrice(new BigDecimal("3400.00"));
        existing.setPriceSource(QuoteSheetItemPrice.SOURCE_MANUAL);
        QuoteSheet sheet = sheetWith(existing);
        stubSheetAndDerivation(sheet, "3500.00");

        store().updateItem(SHEET_ID, ITEM_ID, request("3500.00"), null);

        QuoteSheetItemPrice row = sheet.getItems().get(0).getPrices().get(0);
        assertThat(row.getId()).isEqualTo(900L);
        assertThat(row.getPriceSource()).isEqualTo(QuoteSheetItemPrice.SOURCE_PRICE_LIST);
        assertThat(row.getPriceListId()).isEqualTo(300L);
    }

    /** 落库 PRICE_LIST 行不冻结: 价格表调价后读接口展示当前推导价(核心回归)。 */
    @Test
    void storedPriceListRow_followsLaterPriceListChangeOnRead() {
        QuoteSheetItemPrice stored = new QuoteSheetItemPrice();
        stored.setId(900L);
        stored.setBrandName(BRAND);
        stored.setSpotPrice(new BigDecimal("3500.00"));
        stored.setPriceSource(QuoteSheetItemPrice.SOURCE_PRICE_LIST);
        stored.setPriceListId(300L);
        stored.setPriceListReleasedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        QuoteSheet sheet = sheetWith(stored);
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
        // 价格表已调价到 3550
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection("3550.00", "3500.00"));

        QuoteSheetResponse.ItemPriceResponse cell = storeWithStoredRow(sheet, stored)
                .detail(SHEET_ID).items().get(0).prices().get(0);

        assertThat(cell.spotPrice()).isEqualByComparingTo("3550.00");
        assertThat(cell.spotSource()).isEqualTo("PRICE_LIST");
        assertThat(cell.id()).isEqualTo(900L);
    }

    /** 无从推导时用落库的价格表快照兜底, 固化价不凭空消失。 */
    @Test
    void storedPriceListRow_fallsBackToSnapshotWhenNoDerivation() {
        QuoteSheetItemPrice stored = new QuoteSheetItemPrice();
        stored.setId(900L);
        stored.setBrandName(BRAND);
        stored.setSpotPrice(new BigDecimal("3500.00"));
        stored.setPriceSource(QuoteSheetItemPrice.SOURCE_PRICE_LIST);
        stored.setPriceListId(300L);
        QuoteSheet sheet = sheetWith(stored);
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(null, "3500.00"));

        QuoteSheetResponse.ItemPriceResponse cell = storeWithStoredRow(sheet, stored)
                .detail(SHEET_ID).items().get(0).prices().get(0);

        assertThat(cell.spotPrice()).isEqualByComparingTo("3500.00");
        assertThat(cell.spotSource()).isEqualTo("PRICE_LIST");
        assertThat(cell.priceListId()).isEqualTo(300L);
    }

    // ---------------------------------------------------------------- 夹具

    private QuoteSheetStore storeWithStoredRow(QuoteSheet sheet, QuoteSheetItemPrice stored) {
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of(stored));
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
        return store();
    }

    private void stubSheetAndDerivation(QuoteSheet sheet, String derivedPrice) {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(derivedPrice, derivedPrice));
    }

    private static QuoteSheetRequest.ItemRequest request(String price) {
        return new QuoteSheetRequest.ItemRequest(null, "螺纹钢", "HRB400E", 12, "9米", null,
                BigDecimal.ONE, null, null, null,
                List.of(new QuoteSheetRequest.ItemPriceRequest(BRAND,
                        price == null ? null : new BigDecimal(price), null)));
    }

    private static QuoteSheet sheetWith(QuoteSheetItemPrice existing) {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setSheetNo("9001");
        sheet.setName("比价格式单");
        sheet.setOrderDate(LocalDate.of(2026, 9, 28));
        sheet.setRefDate(LocalDate.of(2026, 9, 28));
        sheet.setRefPeriod("09:30");
        sheet.setLengthPremium(new BigDecimal("30"));
        sheet.setStatus("报价");
        sheet.setVersion(1L);
        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(1L);
        brand.setSheet(sheet);
        brand.setBrandName(BRAND);
        brand.setFreight(new BigDecimal("60.00"));
        brand.setSortOrder(0);
        sheet.setBrands(new ArrayList<>(List.of(brand)));
        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(ITEM_ID);
        item.setSheet(sheet);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setPrices(new ArrayList<>());
        if (existing != null) {
            existing.setItem(item);
            item.getPrices().add(existing);
        }
        sheet.setItems(new ArrayList<>(List.of(item)));
        return sheet;
    }

    /** 构造包含若干品牌推导结果的取版结果。 */
    private static QuoteSheetPriceDeriver.BrandSelection selection(String price, String secondPrice) {
        SupplierPriceList list = new SupplierPriceList();
        list.setId(300L);
        list.setSupplierId(SUPPLIER_ID);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName(BRAND);
        list.setReleasedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        list.setStatus(SupplierPriceList.STATUS_ACTIVE);
        SupplierPriceItem item = new SupplierPriceItem();
        item.setId(400L);
        item.setList(list);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setPrice(price == null ? null : new BigDecimal(price));
        Map<String, SupplierPriceItem> items = new LinkedHashMap<>();
        items.put(item.keyOf(), item);
        return new QuoteSheetPriceDeriver.BrandSelection(new LinkedHashMap<>(Map.of(
                BRAND, new QuoteSheetPriceDeriver.BrandEntry(list, items))));
    }
}
