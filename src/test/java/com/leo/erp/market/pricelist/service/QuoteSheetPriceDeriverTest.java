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
 * 读时推导口径测试(契约 4.6 修订 R2): 直接取该品牌的<b>当前</b>价格表价, 不再按报价时刻取版;
 * 价格表改价后推导值必须立即跟着变(没有历史快照)。
 */
@ExtendWith(MockitoExtension.class)
class QuoteSheetPriceDeriverTest {

    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 9, 28, 8, 0);

    @Mock
    private SupplierPriceListRepository listRepository;

    @Mock
    private SupplierPriceItemRepository itemRepository;

    private QuoteSheetPriceDeriver deriver() {
        return new QuoteSheetPriceDeriver(
                new SupplierPriceListQueryService(listRepository, itemRepository, null));
    }

    /** 主路径: 取该品牌当前价格表的条目价。 */
    @Test
    void derivesPriceFromCurrentList() {
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(item(10L, list, "3220.00")));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isTrue();
        assertThat(spot.price()).isEqualByComparingTo("3220.00");
        assertThat(spot.priceListId()).isEqualTo(1L);
    }

    /** 取消版本语义的核心取舍: 改价后同一个品牌再次推导必须立即拿到新价(历史单据不再钉住旧价)。 */
    @Test
    void reDeriveAfterPriceChangeReflectsCurrentPriceImmediately() {
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(item(10L, list, "3220.00")));

        QuoteSheetPriceDeriver.BrandSelection before =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());
        assertThat(QuoteSheetPriceDeriver.derive(before.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, "9米").price()).isEqualByComparingTo("3220.00");

        // 调价到 3400 且价格表 updated_at 前移
        SupplierPriceList changed = list(1L, "安徽富鑫", MORNING.plusHours(2));
        SupplierPriceItem changedItem = item(10L, changed, "3400.00");
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(changed));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(changedItem));

        QuoteSheetPriceDeriver.BrandSelection after =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());
        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                after.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.price()).isEqualByComparingTo("3400.00");
        assertThat(spot.priceListUpdatedAt()).isEqualTo(MORNING.plusHours(2));
    }

    /** 定尺写法归一: 条目存 {@code 9米}, 比价行写 {@code 9m} / {@code 9 米} 仍必须命中。 */
    @Test
    void matchesEntryWhenLengthSpellingDiffers() {
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(item(10L, list, "3220.00")));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, "9m").price()).isEqualByComparingTo("3220.00");
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, " 9 米 ").price()).isEqualByComparingTo("3220.00");
        // 归一后不同定尺仍不得串味
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, "12米").reason()).isEqualTo(SpotReason.NO_ITEM);
    }

    /** 条目不存在(无价格表/有表无条目/不报价)的三种原因必须可区分。 */
    @Test
    void reportsNoListWhenBrandHasNoCurrentList() {
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of());

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isFalse();
        assertThat(spot.reason()).isEqualTo(SpotReason.NO_LIST);
    }

    @Test
    void reportsNoItemWhenKeyMissing() {
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(item(10L, list, "3220.00")));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 11, "9米");
        assertThat(spot.reason()).isEqualTo(SpotReason.NO_ITEM);
    }

    /** 条目存在但 price 为 NULL(不报价) → NO_PRICE, 绝不当作 0。 */
    @Test
    void reportsNoPriceWhenItemHasNullPrice() {
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(item(10L, list, null)));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isFalse();
        assertThat(spot.reason()).isEqualTo(SpotReason.NO_PRICE);
        assertThat(spot.price()).isNull();
    }

    /** 供应商白名单生效: 不在白名单内的价格表不得被选中。 */
    @Test
    void respectsSupplierWhitelist() {
        SupplierPriceList other = list(9L, "安徽富鑫", MORNING);
        other.setSupplierId(888L);
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(other));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of(777L));

        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, "9米").reason()).isEqualTo(SpotReason.NO_LIST);
    }

    // ---------------------------------------------------------------- 类别别名(直条 ≡ 螺纹钢)

    /** 真实主路径: 价格表条目存 md_material 的 {@code 直条}, 比价单行写 {@code 螺纹钢}。 */
    @Test
    void derivesPriceWhenEntryUsesZhitiaoAndQuoteRowUsesLuowengang() {
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        SupplierPriceItem entry = item(10L, list, "3220.00");
        entry.setCategory(CategoryNormalizer.MATERIAL_CATEGORY_REBAR);
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(entry));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), "螺纹钢", "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isTrue();
        assertThat(spot.price()).isEqualByComparingTo("3220.00");
    }

    /** 反向: 价格表条目存 {@code 螺纹钢}, 比价单行写 {@code 直条}。 */
    @Test
    void derivesPriceWhenEntryUsesLuowengangAndQuoteRowUsesZhitiao() {
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        SupplierPriceItem entry = item(10L, list, "3220.00");
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(entry));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf("安徽富鑫"), CategoryNormalizer.MATERIAL_CATEGORY_REBAR, "抗震钢E", 12, "9米");
        assertThat(spot.matched()).isTrue();
        assertThat(spot.price()).isEqualByComparingTo("3220.00");
    }

    /** 同一 (material, spec, length) 下同时有 直条 与 盘螺 时不得串味。 */
    @Test
    void normalizedMatchDoesNotBleedAcrossDifferentCategories() {
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        SupplierPriceItem rebar = item(10L, list, "3220.00");
        rebar.setCategory(CategoryNormalizer.MATERIAL_CATEGORY_REBAR);
        SupplierPriceItem wireRod = item(11L, list, "3500.00");
        wireRod.setCategory("盘螺");
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(rebar, wireRod));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

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
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        SupplierPriceItem exact = item(10L, list, "1111.00");
        exact.setCategory(CategoryNormalizer.MATERIAL_CATEGORY_REBAR);
        SupplierPriceItem aliasTarget = item(11L, list, "9999.00");
        aliasTarget.setCategory("螺纹钢");
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(exact, aliasTarget));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

        // 单据写 直条: 必须精确命中 直条 那条, 而不是规范化后的 螺纹钢 那条
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                CategoryNormalizer.MATERIAL_CATEGORY_REBAR, "抗震钢E", 12, "9米").price())
                .isEqualByComparingTo("1111.00");
    }

    /** 规范化回退只改类别, 材质/规格/定尺仍须相等。 */
    @Test
    void normalizedMatchStillRequiresMaterialSpecAndLength() {
        SupplierPriceList list = list(1L, "安徽富鑫", MORNING);
        SupplierPriceItem entry = item(10L, list, "3220.00");
        entry.setCategory(CategoryNormalizer.MATERIAL_CATEGORY_REBAR);
        when(listRepository.findCurrentByBrandNames(any())).thenReturn(List.of(list));
        when(itemRepository.findByListIdIn(any())).thenReturn(List.of(entry));

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of("安徽富鑫"), List.of());

        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 16, "9米").reason()).isEqualTo(SpotReason.NO_ITEM);
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "抗震钢E", 12, "12米").reason()).isEqualTo(SpotReason.NO_ITEM);
        assertThat(QuoteSheetPriceDeriver.derive(selection.entryOf("安徽富鑫"),
                "螺纹钢", "HRB400E", 12, "9米").reason()).isEqualTo(SpotReason.NO_ITEM);
    }

    private static SupplierPriceList list(Long id, String brandName, LocalDateTime updatedAt) {
        SupplierPriceList list = new SupplierPriceList();
        list.setId(id);
        list.setSupplierId(777L);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName(brandName);
        list.setReleasedAt(updatedAt);
        list.setUpdatedAt(updatedAt);
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
