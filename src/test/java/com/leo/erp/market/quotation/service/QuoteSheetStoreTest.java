package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSheetStoreTest {

    @Mock
    private QuoteSheetRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SupplierQuery supplierQuery;

    private QuoteSheetStore store() {
        return new QuoteSheetStore(repository, snowflakeIdGenerator, supplierQuery);
    }

    @Test
    void create_assignsSnowflakeIdAndSheetNo() {
        when(snowflakeIdGenerator.nextId()).thenReturn(100L, 201L, 202L, 301L, 302L);
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetResponse response = store().create(request());

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.sheetNo()).isEqualTo("100");
        assertThat(response.brands()).hasSize(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).prices()).hasSize(1);
        assertThat(response.lengthPremium()).isEqualByComparingTo("30");
        verify(repository).saveAndFlush(any(QuoteSheet.class));
    }

    @Test
    void create_rejectsDuplicateBrand() {
        QuoteSheetRequest base = request();
        QuoteSheetRequest duplicated = new QuoteSheetRequest(
                base.name(), base.projectId(), base.projectName(), base.orderDate(), base.refDate(), base.refPeriod(),
                base.lengthPremium(), base.locked(), base.status(), base.remark(),
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 0),
                        new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 1)),
                base.items());

        assertThatThrownBy(() -> store().create(duplicated))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌重复");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsPriceBrandNotInBrandList() {
        QuoteSheetRequest base = request();
        QuoteSheetRequest invalid = new QuoteSheetRequest(
                base.name(), base.projectId(), base.projectName(), base.orderDate(), base.refDate(), base.refPeriod(),
                base.lengthPremium(), base.locked(), base.status(), base.remark(), base.brands(),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("亚新", new BigDecimal("3280"), null)))));

        assertThatThrownBy(() -> store().create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void delete_softDeletesExistingSheet() {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(9L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sheet));
        when(repository.save(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        store().delete(9L);

        assertThat(sheet.isDeletedFlag()).isTrue();
        verify(repository).save(sheet);
    }

    @Test
    void detail_rejectsMissingSheet() {
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> store().detail(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报价单不存在");
    }

    @Test
    void update_rejectsRefChangeWhenLocked() {
        QuoteSheet existing = lockedSheet();
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        QuoteSheetRequest changedRef = new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 11), "9:30 上午",
                new BigDecimal("30"), true, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));

        assertThatThrownBy(() -> store().update(9L, changedRef, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已锁定");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_allowsRefChangeAfterUnlock() {
        QuoteSheet existing = lockedSheet();
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest changedRef = new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 11), "9:30 上午",
                new BigDecimal("30"), false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));

        QuoteSheetResponse response = store().update(9L, changedRef, null);

        assertThat(response.locked()).isFalse();
        assertThat(response.refDate()).isEqualTo(LocalDate.of(2026, 9, 11));
    }

    @Test
    void update_acceptsMatchingExpectedVersion() {
        QuoteSheet existing = new QuoteSheet();
        existing.setId(9L);
        existing.setVersion(3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetResponse response = store().update(9L, request(), 3L);

        assertThat(response.name()).isEqualTo("9月9日报单");
        verify(repository).saveAndFlush(any(QuoteSheet.class));
    }

    @Test
    void update_rejectsStaleExpectedVersion() {
        QuoteSheet existing = new QuoteSheet();
        existing.setId(9L);
        existing.setVersion(3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().update(9L, request(), 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据已被他人修改");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_headerOnly_keepsExistingBrandsAndItems() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest headerOnly = new QuoteSheetRequest(
                "改名后的报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("35"), true, "报价", "备注", null, null);

        QuoteSheetResponse response = store().update(9L, headerOnly, 3L);

        assertThat(response.name()).isEqualTo("改名后的报单");
        assertThat(response.lengthPremium()).isEqualByComparingTo("35");
        assertThat(response.brands()).hasSize(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).prices()).hasSize(1);
    }

    @Test
    void addItem_appendsWithNextLineNoAndResolvesSupplier() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L, 888L);
        when(supplierQuery.findActiveById(77L))
                .thenReturn(Optional.of(new SupplierQuery.SupplierSnapshot(
                        77L, "S001", "杭州物资有限公司", "杭州物资")));

        QuoteSheetResponse.ItemResponse added = store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", new BigDecimal("5"),
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3300"), 77L))),
                1L);

        assertThat(added.id()).isEqualTo(777L);
        assertThat(added.lineNo()).isEqualTo(2);
        assertThat(added.prices().get(0).supplierName()).isEqualTo("杭州物资");
        assertThat(existing.getItems()).hasSize(2);
    }

    @Test
    void addItem_rejectsStaleExpectedVersion() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(2L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE, List.of()), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据已被他人修改");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateItem_replacesFieldsAndPrices() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(4L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(snowflakeIdGenerator.nextId()).thenReturn(555L);

        QuoteSheetResponse.ItemResponse updated = store().updateItem(9L, 301L,
                new QuoteSheetRequest.ItemRequest("高线", "HPB300", 10, "12米", new BigDecimal("2.5"),
                        List.of(new QuoteSheetRequest.ItemPriceRequest("亚新", new BigDecimal("3400"), null))),
                4L);

        assertThat(updated.category()).isEqualTo("高线");
        assertThat(updated.ton()).isEqualByComparingTo("2.5");
        assertThat(updated.prices()).hasSize(1);
        assertThat(updated.prices().get(0).brandName()).isEqualTo("亚新");
    }

    @Test
    void updateItem_rejectsMissingItem() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().updateItem(9L, 999L,
                new QuoteSheetRequest.ItemRequest("高线", "HPB300", 10, "12米", BigDecimal.ONE, List.of()), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品行不存在");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deleteItem_removesItem() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        store().deleteItem(9L, 301L, 1L);

        assertThat(existing.getItems()).isEmpty();
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void deleteItem_rejectsMissingItem() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().deleteItem(9L, 999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品行不存在");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_resolvesSupplierNameSnapshot() {
        when(snowflakeIdGenerator.nextId()).thenReturn(100L, 201L, 202L, 301L);
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(supplierQuery.findActiveById(77L))
                .thenReturn(Optional.of(new SupplierQuery.SupplierSnapshot(
                        77L, "S001", "杭州物资有限公司", "杭州物资")));

        QuoteSheetResponse response = store().create(requestWithSupplier(77L));

        QuoteSheetResponse.ItemPriceResponse price = response.items().get(0).prices().get(0);
        assertThat(price.supplierId()).isEqualTo(77L);
        assertThat(price.supplierName()).isEqualTo("杭州物资");
    }

    @Test
    void create_rejectsUnknownSupplier() {
        when(supplierQuery.findActiveById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store().create(requestWithSupplier(404L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在");
        verify(repository, never()).saveAndFlush(any());
    }

    private QuoteSheet lockedSheet() {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(9L);
        sheet.setLocked(true);
        sheet.setRefDate(LocalDate.of(2026, 9, 10));
        sheet.setRefPeriod("9:30 上午");
        return sheet;
    }

    /** 含一条商品行(含一条现货价)的单据。 */
    private QuoteSheet sheetWithItem(Long id) {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(id);
        sheet.setRefDate(LocalDate.of(2026, 9, 10));
        sheet.setRefPeriod("9:30 上午");
        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(201L);
        brand.setSheet(sheet);
        brand.setBrandName("中天");
        brand.setFreight(new BigDecimal("30"));
        brand.setSortOrder(0);
        sheet.getBrands().add(brand);
        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(301L);
        item.setSheet(sheet);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setTon(BigDecimal.TEN);
        QuoteSheetItemPrice price = new QuoteSheetItemPrice();
        price.setId(401L);
        price.setItem(item);
        price.setBrandName("中天");
        price.setSpotPrice(new BigDecimal("3280"));
        item.getPrices().add(price);
        sheet.getItems().add(item);
        return sheet;
    }

    private QuoteSheetRequest requestWithSupplier(Long supplierId) {
        return new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", new BigDecimal("10"),
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), supplierId)))));
    }

    private QuoteSheetRequest request() {
        return new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", new BigDecimal("10"),
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));
    }
}
