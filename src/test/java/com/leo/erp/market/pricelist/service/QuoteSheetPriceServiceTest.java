package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteSheetItemPriceRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 比价单价格格读时推导语义测试。
 *
 * <p>现货价<b>只</b>来自当前供应商价格表: {@code mk_quote_item_price} 的落库值(含旧手填覆盖)
 * 一律不再参与读, 因此本类只覆盖推导与三种未命中原因。</p>
 */
@ExtendWith(MockitoExtension.class)
class QuoteSheetPriceServiceTest {

    private static final long SHEET_ID = 9007199254740993L;
    private static final long ITEM_ID = 500L;
    private static final long SUPPLIER_ID = 777L;
    private static final String BRAND = "安徽富鑫";

    @Mock
    private QuoteSheetPriceDeriver deriver;

    @Mock
    private QuoteSheetRepository quoteSheetRepository;

    @Mock
    private QuoteSheetItemPriceRepository itemPriceRepository;

    @Mock
    private com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository quoteProjectConfigRepository;

    private QuoteSheetPriceService service() {
        return new QuoteSheetPriceService(deriver, quoteSheetRepository, quoteProjectConfigRepository);
    }

    private static QuoteSheet sheet() {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setOrderDate(LocalDate.of(2026, 9, 28));
        sheet.setRefPeriod("09:30");
        sheet.setBrands(new ArrayList<>(List.of(brand())));
        sheet.setItems(new ArrayList<>(List.of(item())));
        return sheet;
    }

    private static QuoteSheetBrand brand() {
        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(1L);
        brand.setBrandName(BRAND);
        brand.setFreight(new BigDecimal("60.00"));
        brand.setSortOrder(0);
        return brand;
    }

    private static QuoteSheetItem item() {
        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(ITEM_ID);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("抗震钢E");
        item.setSpec(12);
        item.setLength("9米");
        item.setPrices(new ArrayList<>());
        return item;
    }

    private static QuoteSheetPriceDeriver.BrandSelection selection(BigDecimal price) {
        com.leo.erp.market.pricelist.domain.entity.SupplierPriceList list =
                new com.leo.erp.market.pricelist.domain.entity.SupplierPriceList();
        list.setId(300L);
        list.setSupplierId(SUPPLIER_ID);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName(BRAND);
        list.setUpdatedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        list.setStatus(com.leo.erp.market.pricelist.domain.entity.SupplierPriceList.STATUS_ACTIVE);
        com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem priceItem =
                new com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem();
        priceItem.setId(400L);
        priceItem.setList(list);
        priceItem.setCategory("螺纹钢");
        priceItem.setMaterial("抗震钢E");
        priceItem.setSpec(12);
        priceItem.setLength("9米");
        priceItem.setPrice(price);
        return new QuoteSheetPriceDeriver.BrandSelection(Map.of(BRAND,
                new QuoteSheetPriceDeriver.BrandEntry(list, Map.of(priceItem.keyOf(), priceItem))));
    }

    /** 有价格表但没有匹配条目 → 推导结果为 NO_ITEM(与"无价格表"NO_LIST 严格区分)。 */
    private static QuoteSheetPriceDeriver.BrandSelection selectionWithoutMatchingItem() {
        com.leo.erp.market.pricelist.domain.entity.SupplierPriceList list =
                new com.leo.erp.market.pricelist.domain.entity.SupplierPriceList();
        list.setId(300L);
        list.setSupplierId(SUPPLIER_ID);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName(BRAND);
        list.setUpdatedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        return new QuoteSheetPriceDeriver.BrandSelection(Map.of(BRAND,
                new QuoteSheetPriceDeriver.BrandEntry(list, Map.of())));
    }

    @Test
    void toCells_derivesPriceAndSupplierFromPriceList() {
        when(deriver.selectBrands(any(), any())).thenReturn(selection(new BigDecimal("3220.00")));

        Map<String, QuoteSheetResponse.ItemPriceResponse> cells =
                service().toCells(sheet()).get(ITEM_ID);

        QuoteSheetResponse.ItemPriceResponse cell = cells.get(BRAND);
        assertThat(cell.spotPrice()).isEqualByComparingTo("3220.00");
        assertThat(cell.derivedSpotPrice()).isEqualByComparingTo("3220.00");
        assertThat(cell.spotSource()).isEqualTo("PRICE_LIST");
        assertThat(cell.spotReason()).isNull();
        assertThat(cell.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(cell.supplierName()).isEqualTo("杭州中金钢铁");
        assertThat(cell.priceListId()).isEqualTo(300L);
        // 兼容字段: 已取消版本语义, 填价格表 updated_at
        assertThat(cell.priceListReleasedAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 8, 0));
        assertThat(cell.freight()).isEqualByComparingTo("60.00");
    }

    /**
     * 关键回归: {@code mk_quote_item_price} 里落库的手填值<b>不得</b>影响读结果
     * (手填覆盖已彻底删除, 读路径也不再查该表)。
     */
    @Test
    void toCells_ignoresStoredManualRowEntirely() {
        QuoteSheet sheet = sheet();
        sheet.getItems().get(0).getPrices().add(storedRow("3100.00", QuoteSheetItemPrice.SOURCE_MANUAL));
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
        when(deriver.selectBrands(any(), any())).thenReturn(selection(new BigDecimal("3220.00")));

        QuoteSheetResponse.ItemPriceResponse cell =
                service().deriveCells(SHEET_ID).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isEqualByComparingTo("3220.00");
        assertThat(cell.spotSource()).isEqualTo("PRICE_LIST");
        assertThat(cell.priceListId()).isEqualTo(300L);
        // 落地的手填价 3100 被彻底忽略
        assertThat(cell.spotPrice()).isNotEqualByComparingTo("3100.00");
        // 读路径根本不查 mk_quote_item_price
        verify(itemPriceRepository, never()).findBySheetId(anyLong());
    }

    /**
     * 无价格表价时不得回退落库快照: 即使 {@code mk_quote_item_price} 里有价(手填或固化),
     * 也一律 {@code NONE} + 原因(无价格表 → {@code NO_LIST})。
     */
    @Test
    void toCells_doesNotFallBackToStoredSnapshotWhenNoDerivation() {
        QuoteSheet sheet = sheet();
        sheet.getItems().get(0).getPrices().add(storedRow("3100.00", QuoteSheetItemPrice.SOURCE_PRICE_LIST));
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet));
        when(deriver.selectBrands(any(), any()))
                .thenReturn(new QuoteSheetPriceDeriver.BrandSelection(Map.of(
                        BRAND, new QuoteSheetPriceDeriver.BrandEntry(null, Map.of()))));

        QuoteSheetResponse.ItemPriceResponse cell =
                service().deriveCells(SHEET_ID).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isNull();
        assertThat(cell.spotSource()).isEqualTo("NONE");
        assertThat(cell.spotReason()).isEqualTo(SpotReason.NO_LIST.name());
        assertThat(cell.priceListId()).isNull();
        assertThat(cell.priceListReleasedAt()).isNull();
    }

    /** 造一条"历史落库现货价行"(读路径必须完全忽略它)。 */
    private static QuoteSheetItemPrice storedRow(String price, String source) {
        QuoteSheetItemPrice row = new QuoteSheetItemPrice();
        row.setId(800L);
        row.setItem(item());
        row.setBrandName(BRAND);
        row.setSpotPrice(new BigDecimal(price));
        row.setPriceSource(source);
        row.setPriceListId(999L);
        row.setPriceListReleasedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        return row;
    }

    /** 有价格表但无匹配条目 → NO_ITEM; 条目存在但不报价 → NO_PRICE。 */
    @Test
    void toCells_reportsNoItemWhenEntryMissing() {
        when(deriver.selectBrands(any(), any())).thenReturn(selectionWithoutMatchingItem());

        QuoteSheetResponse.ItemPriceResponse cell = service().toCells(sheet()).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isNull();
        assertThat(cell.spotSource()).isEqualTo("NONE");
        assertThat(cell.spotReason()).isEqualTo(SpotReason.NO_ITEM.name());
    }

    @Test
    void toCells_reportsNoPriceWhenItemNotQuoted() {
        when(deriver.selectBrands(any(), any())).thenReturn(selection(null));

        QuoteSheetResponse.ItemPriceResponse cell = service().toCells(sheet()).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isNull();
        assertThat(cell.spotSource()).isEqualTo("NONE");
        assertThat(cell.spotReason()).isEqualTo(SpotReason.NO_PRICE.name());
    }

    @Test
    void deriveCells_throwsNotFoundForMissingSheet() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deriveCells(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报价单不存在");
    }

    // ---------------------------------------------------------------- 定尺加价推算(契约 ②)

    /**
     * 项目配置了定尺加价 + 该定尺缺条目 → 用同(类别,材质,规格)的另一条定尺价 + 加价推算,
     * 响应必须能区分出推算来源。
     */
    @Test
    void toCells_derivesMissingLengthWithProjectPremium() {
        QuoteSheet sheet = sheet();
        sheet.setProjectId(7L);
        sheet.setLengthPremium(new BigDecimal("30.00"));
        sheet.getItems().get(0).setLength("12米");
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(config(new BigDecimal("30.00"))));
        when(deriver.selectBrands(any(), any())).thenReturn(selectionForLength("9米", "3100.00"));

        QuoteSheetResponse.ItemPriceResponse cell = service().toCells(sheet).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isEqualByComparingTo("3130.00");
        assertThat(cell.spotSource()).isEqualTo(
                QuoteSheetPriceDeriver.SOURCE_PRICE_LIST_LENGTH_DERIVED);
        assertThat(cell.derivedFromLength()).isEqualTo("9米");
        assertThat(cell.lengthPremiumApplied()).isEqualByComparingTo("30.00");
        assertThat(cell.spotReason()).isNull();
    }

    /** 项目未配置(无配置行) → 不推算, 缺定尺保持 NO_ITEM(不得用单据上的默认 30 推算)。 */
    @Test
    void toCells_doesNotDeriveWhenProjectNotConfigured() {
        QuoteSheet sheet = sheet();
        sheet.setProjectId(7L);
        sheet.setLengthPremium(new BigDecimal("30.00"));
        sheet.getItems().get(0).setLength("12米");
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.empty());
        when(deriver.selectBrands(any(), any())).thenReturn(selectionForLength("9米", "3100.00"));

        QuoteSheetResponse.ItemPriceResponse cell = service().toCells(sheet).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isNull();
        assertThat(cell.spotSource()).isEqualTo("NONE");
        assertThat(cell.spotReason()).isEqualTo(SpotReason.NO_ITEM.name());
        assertThat(cell.derivedFromLength()).isNull();
        assertThat(cell.lengthPremiumApplied()).isNull();
    }

    /** 项目已配置但加价为 0 → 不推算(不产生"等值推算")。 */
    @Test
    void toCells_doesNotDeriveWhenPremiumIsZero() {
        QuoteSheet sheet = sheet();
        sheet.setProjectId(7L);
        sheet.setLengthPremium(BigDecimal.ZERO);
        sheet.getItems().get(0).setLength("12米");
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(config(BigDecimal.ZERO)));
        when(deriver.selectBrands(any(), any())).thenReturn(selectionForLength("9米", "3100.00"));

        QuoteSheetResponse.ItemPriceResponse cell = service().toCells(sheet).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isNull();
        assertThat(cell.spotReason()).isEqualTo(SpotReason.NO_ITEM.name());
    }

    /** 定尺命中绝对价时来源仍是 PRICE_LIST, 不带推算字段。 */
    @Test
    void toCells_keepsAbsolutePriceSourceWhenLengthEntryExists() {
        QuoteSheet sheet = sheet();
        sheet.setProjectId(7L);
        sheet.setLengthPremium(new BigDecimal("30.00"));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(config(new BigDecimal("30.00"))));
        when(deriver.selectBrands(any(), any())).thenReturn(selectionForLength("9米", "3100.00"));

        QuoteSheetResponse.ItemPriceResponse cell = service().toCells(sheet).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isEqualByComparingTo("3100.00");
        assertThat(cell.spotSource()).isEqualTo(QuoteSheetPriceDeriver.SOURCE_PRICE_LIST);
        assertThat(cell.derivedFromLength()).isNull();
        assertThat(cell.lengthPremiumApplied()).isNull();
    }

    private static com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig config(BigDecimal premium) {
        com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig config =
                new com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig();
        config.setId(70L);
        config.setProjectId(7L);
        config.setLengthPremium(premium);
        return config;
    }

    /** 造"价格表里只有某个定尺条目"的选中结果, 供定尺加价推算用例使用。 */
    private static QuoteSheetPriceDeriver.BrandSelection selectionForLength(String length, String price) {
        com.leo.erp.market.pricelist.domain.entity.SupplierPriceList list =
                new com.leo.erp.market.pricelist.domain.entity.SupplierPriceList();
        list.setId(300L);
        list.setSupplierId(SUPPLIER_ID);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName(BRAND);
        list.setUpdatedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        list.setStatus(com.leo.erp.market.pricelist.domain.entity.SupplierPriceList.STATUS_ACTIVE);
        com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem priceItem =
                new com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem();
        priceItem.setId(400L);
        priceItem.setList(list);
        priceItem.setCategory("螺纹钢");
        priceItem.setMaterial("抗震钢E");
        priceItem.setSpec(12);
        priceItem.setLength(length);
        priceItem.setPrice(new BigDecimal(price));
        return new QuoteSheetPriceDeriver.BrandSelection(Map.of(BRAND,
                new QuoteSheetPriceDeriver.BrandEntry(list, Map.of(priceItem.keyOf(), priceItem))));
    }
}
