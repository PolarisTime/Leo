package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.web.dto.PricePullRequest;
import com.leo.erp.market.pricelist.web.dto.PricePullResponse;
import com.leo.erp.market.pricelist.web.dto.QuoteSheetPriceCellRequest;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteSheetItemPriceRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.SupplierQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * 比价单价格格读时推导 + 单格覆盖 + 可选固化的语义测试。
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
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SupplierQuery supplierQuery;

    private QuoteSheetPriceService service() {
        return new QuoteSheetPriceService(deriver, quoteSheetRepository,
                itemPriceRepository, snowflakeIdGenerator, supplierQuery);
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

    private static QuoteSheetPriceDeriver.BrandSelection selection(SupplierPriceListStub stub) {
        com.leo.erp.market.pricelist.domain.entity.SupplierPriceList list =
                new com.leo.erp.market.pricelist.domain.entity.SupplierPriceList();
        list.setId(300L);
        list.setSupplierId(SUPPLIER_ID);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName(BRAND);
        list.setReleasedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        list.setStatus(com.leo.erp.market.pricelist.domain.entity.SupplierPriceList.STATUS_ACTIVE);
        com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem priceItem =
                new com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem();
        priceItem.setId(400L);
        priceItem.setList(list);
        priceItem.setCategory("螺纹钢");
        priceItem.setMaterial("抗震钢E");
        priceItem.setSpec(12);
        priceItem.setLength("9米");
        priceItem.setPrice(stub.price());
        return new QuoteSheetPriceDeriver.BrandSelection(Map.of(BRAND,
                new QuoteSheetPriceDeriver.BrandEntry(list, Map.of(priceItem.keyOf(), priceItem))));
    }

    private record SupplierPriceListStub(BigDecimal price) {
    }

    @Test
    void toCells_derivesPriceAndSupplierFromPriceList() {
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(new SupplierPriceListStub(new BigDecimal("3220.00"))));
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of());

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
        assertThat(cell.freight()).isEqualByComparingTo("60.00");
    }

    /** MANUAL 覆盖行优先于推导值, 且同时保留推导值供"恢复为价格表价"预览。 */
    @Test
    void toCells_manualOverrideTakesPrecedenceOverDerivedPrice() {
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(new SupplierPriceListStub(new BigDecimal("3220.00"))));
        QuoteSheetItemPrice manual = new QuoteSheetItemPrice();
        manual.setId(800L);
        manual.setItem(item());
        manual.setBrandName(BRAND);
        manual.setSpotPrice(new BigDecimal("3100.00"));
        manual.setPriceSource(QuoteSheetItemPrice.SOURCE_MANUAL);
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of(manual));

        QuoteSheetResponse.ItemPriceResponse cell = service().toCells(sheet()).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isEqualByComparingTo("3100.00");
        assertThat(cell.spotSource()).isEqualTo("MANUAL");
        assertThat(cell.derivedSpotPrice()).isEqualByComparingTo("3220.00");
        assertThat(cell.id()).isEqualTo(800L);
        assertThat(cell.priceSource()).isEqualTo(QuoteSheetItemPrice.SOURCE_MANUAL);
    }

    @Test
    void toCells_reportsNoListAtTimeWhenNoVersion() {
        when(deriver.selectBrands(any(), any(), any()))
                .thenReturn(new QuoteSheetPriceDeriver.BrandSelection(Map.of(
                        BRAND, new QuoteSheetPriceDeriver.BrandEntry(null, Map.of()))));
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of());

        QuoteSheetResponse.ItemPriceResponse cell = service().toCells(sheet()).get(ITEM_ID).get(BRAND);

        assertThat(cell.spotPrice()).isNull();
        assertThat(cell.spotSource()).isEqualTo("NONE");
        assertThat(cell.spotReason()).isEqualTo(SpotReason.NO_LIST_AT_TIME.name());
    }

    @Test
    void toCells_reportsNoItemWhenEntryMissing() {
        when(deriver.selectBrands(any(), any(), any()))
                .thenReturn(selection(new SupplierPriceListStub(null)));
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of());

        // stub 条目 price 为 null → NO_PRICE; 改为完全无条目即 NO_ITEM 由 deriver 测试覆盖
        QuoteSheetResponse.ItemPriceResponse cell = service().toCells(sheet()).get(ITEM_ID).get(BRAND);
        assertThat(cell.spotReason()).isEqualTo(SpotReason.NO_PRICE.name());
    }

    @Test
    void overrideCell_writesManualSourceAndKeepsSnapshot() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));
        when(itemPriceRepository.findByItemIdAndBrandName(ITEM_ID, BRAND)).thenReturn(Optional.empty());
        when(snowflakeIdGenerator.nextId()).thenReturn(900L);
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(new SupplierPriceListStub(new BigDecimal("3220.00"))));
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of());

        QuoteSheetResponse.ItemPriceResponse cell = service().overrideCell(SHEET_ID, ITEM_ID, BRAND,
                new QuoteSheetPriceCellRequest(new BigDecimal("3150.00"), SUPPLIER_ID, null), 7L);

        ArgumentCaptor<QuoteSheetItemPrice> captor = ArgumentCaptor.forClass(QuoteSheetItemPrice.class);
        verify(itemPriceRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPriceSource()).isEqualTo(QuoteSheetItemPrice.SOURCE_MANUAL);
        assertThat(captor.getValue().getSpotPrice()).isEqualByComparingTo("3150.00");
        assertThat(captor.getValue().getPriceListId()).isNull();
        assertThat(captor.getValue().getPriceListReleasedAt()).isNull();
        assertThat(cell).isNotNull();
    }

    @Test
    void overrideCell_rejectsNegativePriceAndUnknownBrand() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));

        assertThatThrownBy(() -> service().overrideCell(SHEET_ID, ITEM_ID, BRAND,
                new QuoteSheetPriceCellRequest(new BigDecimal("-1"), null, null), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价不能为负");
        assertThatThrownBy(() -> service().overrideCell(SHEET_ID, ITEM_ID, "不存在的品牌",
                new QuoteSheetPriceCellRequest(BigDecimal.ONE, null, null), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌不在该单据品牌列中");
        verify(itemPriceRepository, never()).saveAndFlush(any());
    }

    /** path 变量传入的 brandName 边界: 空串/超 64/含控制字符一律 422, 不得 500 或路由 404。 */
    @Test
    void overrideCell_rejectsInvalidBrandNamePathValues() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));
        String tooLong = "品".repeat(65);

        assertThatThrownBy(() -> service().overrideCell(SHEET_ID, ITEM_ID, "  ",
                new QuoteSheetPriceCellRequest(BigDecimal.ONE, null, null), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌不能为空");
        assertThatThrownBy(() -> service().overrideCell(SHEET_ID, ITEM_ID, tooLong,
                new QuoteSheetPriceCellRequest(BigDecimal.ONE, null, null), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌长度不能超过64个字符");
        assertThatThrownBy(() -> service().overrideCell(SHEET_ID, ITEM_ID, "富鑫\n钢",
                new QuoteSheetPriceCellRequest(BigDecimal.ONE, null, null), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌含非法字符");
        verify(itemPriceRepository, never()).saveAndFlush(any());
    }

    @Test
    void clearCell_rejectsInvalidBrandNamePathValues() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));

        assertThatThrownBy(() -> service().clearCell(SHEET_ID, ITEM_ID, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌不能为空");
        assertThatThrownBy(() -> service().clearCell(SHEET_ID, ITEM_ID, "富鑫\u0000钢"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌含非法字符");
        verify(itemPriceRepository, never()).delete(any());
    }

    @Test
    void overrideCell_throwsNotFoundWhenSheetMissing() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().overrideCell(SHEET_ID, ITEM_ID, BRAND,
                new QuoteSheetPriceCellRequest(BigDecimal.ONE, null, null), 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void clearCell_isIdempotentWhenNoOverrideRowExists() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));
        when(itemPriceRepository.findByItemIdAndBrandName(ITEM_ID, BRAND)).thenReturn(Optional.empty());

        service().clearCell(SHEET_ID, ITEM_ID, BRAND);

        verify(itemPriceRepository, never()).delete(any());
    }

    @Test
    void pull_preservesManualByDefaultAndFillsOnlyDerivedCells() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(new SupplierPriceListStub(new BigDecimal("3220.00"))));
        QuoteSheetItemPrice manual = new QuoteSheetItemPrice();
        manual.setId(800L);
        manual.setItem(item());
        manual.setBrandName(BRAND);
        manual.setSpotPrice(new BigDecimal("3100.00"));
        manual.setPriceSource(QuoteSheetItemPrice.SOURCE_MANUAL);
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of(manual));

        PricePullResponse response = service().pull(SHEET_ID, new PricePullRequest(null, null, null, false));

        assertThat(response.filledCount()).isZero();
        assertThat(response.preservedCount()).isEqualTo(1);
        assertThat(manual.getSpotPrice()).isEqualByComparingTo("3100.00");
        assertThat(manual.getPriceSource()).isEqualTo(QuoteSheetItemPrice.SOURCE_MANUAL);
    }

    @Test
    void pull_overwriteManualReplacesCellAndMarksPriceListSource() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(new SupplierPriceListStub(new BigDecimal("3220.00"))));
        QuoteSheetItemPrice manual = new QuoteSheetItemPrice();
        manual.setId(800L);
        manual.setItem(item());
        manual.setBrandName(BRAND);
        manual.setSpotPrice(new BigDecimal("3100.00"));
        manual.setPriceSource(QuoteSheetItemPrice.SOURCE_MANUAL);
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of(manual));

        PricePullResponse response = service().pull(SHEET_ID, new PricePullRequest(null, null, null, true));

        assertThat(response.filledCount()).isEqualTo(1);
        assertThat(response.preservedCount()).isZero();
        assertThat(manual.getSpotPrice()).isEqualByComparingTo("3220.00");
        assertThat(manual.getPriceSource()).isEqualTo(QuoteSheetItemPrice.SOURCE_PRICE_LIST);
        assertThat(manual.getPriceListId()).isEqualTo(300L);
        assertThat(manual.getSupplierName()).isEqualTo("杭州中金钢铁");
    }

    /** 未匹配行必须带正确原因枚举。 */
    @Test
    void pull_reportsUnmatchedReasonWhenNoPrice() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(new SupplierPriceListStub(null)));
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of());

        PricePullResponse response = service().pull(SHEET_ID, new PricePullRequest(null, null, null, false));

        assertThat(response.filledCount()).isZero();
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.unmatchedRows()).hasSize(1);
        assertThat(response.unmatchedRows().get(0).reason()).isEqualTo(SpotReason.NO_PRICE.name());
        assertThat(response.unmatchedRows().get(0).brandName()).isEqualTo(BRAND);
        assertThat(response.unmatchedRows().get(0).spec()).isEqualTo(12);
    }

    @Test
    void pull_reportsNoListAtTimeWhenBrandHasNoVersion() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));
        when(deriver.selectBrands(any(), any(), any()))
                .thenReturn(new QuoteSheetPriceDeriver.BrandSelection(Map.of(
                        BRAND, new QuoteSheetPriceDeriver.BrandEntry(null, Map.of()))));
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of());

        PricePullResponse response = service().pull(SHEET_ID, new PricePullRequest(null, null, null, false));

        assertThat(response.unmatchedRows().get(0).reason()).isEqualTo(SpotReason.NO_LIST_AT_TIME.name());
    }

    @Test
    void pull_usesSheetQuoteAsOfWhenReleasedBeforeMissing() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(new SupplierPriceListStub(new BigDecimal("3220.00"))));
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of());

        service().pull(SHEET_ID, new PricePullRequest(null, null, null, false));

        ArgumentCaptor<LocalDateTime> asOfCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deriver).selectBrands(any(), any(), asOfCaptor.capture());
        // 单据 2026-09-28 + refPeriod "09:30" → quoteAsOf = 2026-09-28T09:30
        assertThat(asOfCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 28, 9, 30));
    }

    @Test
    void deriveCells_throwsNotFoundForMissingSheet() {
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deriveCells(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报价单不存在");
        verify(itemPriceRepository, never()).saveAndFlush(any());
    }
}
