package com.leo.erp.market.pricelist.service;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import com.leo.erp.market.pricelist.repository.SupplierPriceItemRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceListRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 读时推导取版口径测试: 必须按单据报价时刻(released_at <= quoteAsOf)选版, 且不接受未来版本。
 */
@ExtendWith(MockitoExtension.class)
class QuoteSheetPriceDeriverTest {

    private static final LocalDateTime QUOTE_AS_OF = LocalDateTime.of(2026, 9, 28, 9, 30, 0);

    @Mock
    private SupplierPriceListRepository listRepository;

    @Mock
    private SupplierPriceItemRepository itemRepository;

    private QuoteSheetPriceDeriver deriver() {
        return new QuoteSheetPriceDeriver(
                new SupplierPriceListQueryService(listRepository, itemRepository, null));
    }

    /** 仓库按 released_at DESC, id DESC 返回候选, 取版应取第一条即"该时刻最新版本"。 */
    @Test
    void selectsLatestVersionAtOrBeforeQuoteAsOf() {
        SupplierPriceList morning = list(1L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        SupplierPriceList afternoon = list(2L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 14, 35));
        // 报价时刻 09:30: 下午版在之后发布, 不得被选中
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(morning));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(item(10L, morning, "3220.00")));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isTrue();
        assertThat(spot.price()).isEqualByComparingTo("3220.00");
        assertThat(spot.priceListId()).isEqualTo(1L);
        assertThat(afternoon.getReleasedAt()).isAfter(QUOTE_AS_OF);
    }

    /** 边界: released_at 恰好等于 quoteAsOf 的版本必须生效(<=)。 */
    @Test
    void acceptsVersionReleasedExactlyAtQuoteAsOf() {
        SupplierPriceList boundary = list(3L, "安徽富鑫", QUOTE_AS_OF);
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(boundary));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(item(11L, boundary, "3300.00")));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, "9米").price()).isEqualByComparingTo("3300.00");
    }

    /** 一日多版: 报价时刻之前的两个版本取 released_at 更大的那个。 */
    @Test
    void picksNewerVersionAmongSeveralBeforeQuoteAsOf() {
        LocalDateTime asOf = LocalDateTime.of(2026, 9, 28, 16, 0);
        SupplierPriceList first = list(1L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        SupplierPriceList second = list(2L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 14, 35));
        // 查询已排序: 14:35 在前
        when(listRepository.findActiveAsOf(asOf)).thenReturn(List.of(second, first));
        when(itemRepository.findByListIdIn(any()))
                .thenReturn(List.of(item(20L, second, "3400.00"), item(21L, first, "3000.00")));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), asOf);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.priceListId()).isEqualTo(2L);
        assertThat(spot.price()).isEqualByComparingTo("3400.00");
    }

    @Test
    void reportsNoListAtTimeWhenBrandHasNoVersion() {
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of());

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isFalse();
        assertThat(spot.reason()).isEqualTo(SpotReason.NO_LIST_AT_TIME);
    }

    @Test
    void reportsNoItemWhenKeyMissing() {
        SupplierPriceList list = list(1L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(item(10L, list, "3220.00")));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 11, "9米");
        assertThat(spot.reason()).isEqualTo(SpotReason.NO_ITEM);
    }

    /** 条目存在但 price 为 NULL(不报价) → NO_PRICE, 绝不当作 0。 */
    @Test
    void reportsNoPriceWhenItemHasNullPrice() {
        SupplierPriceList list = list(1L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(item(10L, list, null)));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isFalse();
        assertThat(spot.reason()).isEqualTo(SpotReason.NO_PRICE);
        assertThat(spot.price()).isNull();
    }

    /** 供应商白名单生效: 不在白名单内的版本不得被选中。 */
    @Test
    void respectsSupplierWhitelist() {
        SupplierPriceList other = list(9L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        other.setSupplierId(888L);
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(other));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(777L), QUOTE_AS_OF);

        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, "9米").reason()).isEqualTo(SpotReason.NO_LIST_AT_TIME);
    }

    // ---------------------------------------------------------------- 类别别名(直条 ≡ 螺纹钢)

    /** 真实主路径: 价格表条目存 md_material 的 {@code 直条}, 比价单行写 {@code 螺纹钢}。 */
    @Test
    void derivesPriceWhenEntryUsesZhitiaoAndQuoteRowUsesLuowengang() {
        SupplierPriceList list = list(1L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        SupplierPriceItem entry = item(10L, list, "3220.00");
        entry.setCategory(CategoryNormalizer.MATERIAL_CATEGORY_REBAR);
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(entry));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isTrue();
        assertThat(spot.price()).isEqualByComparingTo("3220.00");
    }

    /** 反向: 价格表条目存 {@code 螺纹钢}, 比价单行写 {@code 直条}。 */
    @Test
    void derivesPriceWhenEntryUsesLuowengangAndQuoteRowUsesZhitiao() {
        SupplierPriceList list = list(1L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        SupplierPriceItem entry = item(10L, list, "3220.00");
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(entry));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), CategoryNormalizer.MATERIAL_CATEGORY_REBAR, "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isTrue();
        assertThat(spot.price()).isEqualByComparingTo("3220.00");
    }

    /** 同一 (material, spec, length) 下同时有 直条 与 盘螺 时不得串味。 */
    @Test
    void normalizedMatchDoesNotBleedAcrossDifferentCategories() {
        SupplierPriceList list = list(1L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        SupplierPriceItem rebar = item(10L, list, "3220.00");
        rebar.setCategory(CategoryNormalizer.MATERIAL_CATEGORY_REBAR);
        SupplierPriceItem wireRod = item(11L, list, "3500.00");
        wireRod.setCategory("盘螺");
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(rebar, wireRod));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        // 螺纹钢(规范化后等于 直条)只能拿到直条价
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, "9米").price()).isEqualByComparingTo("3220.00");
        // 盘螺不受别名影响, 走精确命中
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "盘螺", "抗震钢E", 12, "9米").price()).isEqualByComparingTo("3500.00");
    }

    /** 精确命中优先于规范化命中: 同名不同类别不得被别名规则顶掉。 */
    @Test
    void exactCategoryMatchWinsOverNormalizedMatch() {
        SupplierPriceList list = list(1L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        SupplierPriceItem exact = item(10L, list, "1111.00");
        exact.setCategory(CategoryNormalizer.MATERIAL_CATEGORY_REBAR);
        SupplierPriceItem aliasTarget = item(11L, list, "9999.00");
        aliasTarget.setCategory("螺纹钢");
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(exact, aliasTarget));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        // 单据写 直条: 必须精确命中 直条 那条, 而不是规范化后的 螺纹钢 那条
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                CategoryNormalizer.MATERIAL_CATEGORY_REBAR, "抗震钢E", 12, "9米").price())
                .isEqualByComparingTo("1111.00");
    }

    /** 规范化回退只改类别, 材质/规格/定尺仍须相等。 */
    @Test
    void normalizedMatchStillRequiresMaterialSpecAndLength() {
        SupplierPriceList list = list(1L, "安徽富鑫", LocalDateTime.of(2026, 9, 28, 8, 0));
        SupplierPriceItem entry = item(10L, list, "3220.00");
        entry.setCategory(CategoryNormalizer.MATERIAL_CATEGORY_REBAR);
        when(listRepository.findActiveAsOf(QUOTE_AS_OF)).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(entry));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(), QUOTE_AS_OF);

        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 16, "9米").reason()).isEqualTo(SpotReason.NO_ITEM);
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, "12米").reason()).isEqualTo(SpotReason.NO_ITEM);
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "HRB400E", 12, "9米").reason()).isEqualTo(SpotReason.NO_ITEM);
    }

    private static SupplierPriceList list(Long id, String brandName, LocalDateTime releasedAt) {
        SupplierPriceList list = new SupplierPriceList();
        list.setId(id);
        list.setSupplierId(777L);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName(brandName);
        list.setReleasedAt(releasedAt);
        list.setEffectiveFrom(releasedAt.toLocalDate());
        list.setStatus(SupplierPriceList.STATUS_ACTIVE);
        list.setItems(new ArrayList<>());
        return list;
    }

    private static SupplierPriceItem item(Long id, SupplierPriceList list, String price) {
        SupplierPriceItem item = new SupplierPriceItem();
        item.setId(id);
        item.setList(list);
        item.setCategory("螺纹钢");
        item.setMaterial("抗震钢E");
        item.setSpec(12);
        item.setLength("9米");
        item.setPrice(price == null ? null : new BigDecimal(price));
        item.setSortOrder(0);
        return item;
    }
}
