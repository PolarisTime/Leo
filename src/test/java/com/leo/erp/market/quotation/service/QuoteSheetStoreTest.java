package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.SupplierQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSheetStoreTest {

    @Mock
    private QuoteSheetRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private EntityManager entityManager;

    private QuoteSheetStore store() {
        return new QuoteSheetStore(repository, snowflakeIdGenerator, supplierQuery, entityManager);
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
                base.lengthPremium(), base.locked(), base.specQuantityLocked(), base.status(), base.remark(),
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 0),
                        new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 1)),
                base.items());

        assertThatThrownBy(() -> store().create(duplicated))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌重复");
        verify(repository, never()).saveAndFlush(any());
    }

    /** 回归: 品牌名按 trim 后判重, ["中天"," 中天 "] 必须在入库前报 422, 不得落到唯一键 409。 */
    @Test
    void create_rejectsDuplicateBrandAfterTrim() {
        QuoteSheetRequest base = request();
        QuoteSheetRequest duplicated = new QuoteSheetRequest(
                base.name(), base.projectId(), base.projectName(), base.orderDate(), base.refDate(), base.refPeriod(),
                base.lengthPremium(), base.locked(), base.specQuantityLocked(), base.status(), base.remark(),
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 0),
                        new QuoteSheetRequest.BrandRequest(" 中天 ", BigDecimal.TEN, 1)),
                base.items());

        assertThatThrownBy(() -> store().create(duplicated))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌重复")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 回归: 同一行 prices 内 trim 后重名必须在入库前报 422, 不得落到 uk_quote_item_price 409。 */
    @Test
    void create_rejectsDuplicatePriceBrandAfterTrimWithinRow() {
        QuoteSheetRequest base = request();
        QuoteSheetRequest duplicated = new QuoteSheetRequest(
                base.name(), base.projectId(), base.projectName(), base.orderDate(), base.refDate(), base.refPeriod(),
                base.lengthPremium(), base.locked(), base.specQuantityLocked(), base.status(), base.remark(),
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null),
                                new QuoteSheetRequest.ItemPriceRequest(" 中天 ", new BigDecimal("3290"), null)))));

        assertThatThrownBy(() -> store().create(duplicated))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌重复")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** trim 后命中的现货价品牌属于品牌列表, 应通过并统一按 trim 后名称落库。 */
    @Test
    void create_normalizesTrimmedPriceBrandName() {
        when(snowflakeIdGenerator.nextId()).thenReturn(100L, 201L, 301L, 401L);
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest base = request();
        QuoteSheetRequest trimmed = new QuoteSheetRequest(
                base.name(), base.projectId(), base.projectName(), base.orderDate(), base.refDate(), base.refPeriod(),
                base.lengthPremium(), base.locked(), base.specQuantityLocked(), base.status(), base.remark(),
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest(" 中天 ", new BigDecimal("3280"), null)))));

        QuoteSheetResponse response = store().create(trimmed);

        assertThat(response.items().get(0).prices()).hasSize(1);
        assertThat(response.items().get(0).prices().get(0).brandName()).isEqualTo("中天");
    }

    @Test
    void create_rejectsPriceBrandNotInBrandList() {
        QuoteSheetRequest base = request();
        QuoteSheetRequest invalid = new QuoteSheetRequest(
                base.name(), base.projectId(), base.projectName(), base.orderDate(), base.refDate(), base.refPeriod(),
                base.lengthPremium(), base.locked(), base.specQuantityLocked(), base.status(), base.remark(), base.brands(),
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
                new BigDecimal("30"), true, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));

        assertThatThrownBy(() -> store().update(9L, changedRef, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 回归: 已锁定但请求未携带 locked(未显式解锁) 时, 仍改 refDate 必须 422, 不得被绕过。 */
    @Test
    void update_rejectsRefChangeWhenLockedAndLockFlagOmitted() {
        QuoteSheet existing = lockedSheet();
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        QuoteSheetRequest changedRef = new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 11), "9:30 上午",
                new BigDecimal("30"), null, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));

        assertThatThrownBy(() -> store().update(9L, changedRef, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 回归: 已锁定且未显式解锁时, 仅修改 refPeriod 也必须 422。 */
    @Test
    void update_rejectsRefPeriodChangeWhenLockedAndLockFlagOmitted() {
        QuoteSheet existing = lockedSheet();
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        QuoteSheetRequest changedPeriod = new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10), "10:00 上午",
                new BigDecimal("30"), null, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));

        assertThatThrownBy(() -> store().update(9L, changedPeriod, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
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
                new BigDecimal("30"), false, false, "报价", null,
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
                .hasMessageContaining("版本已变更");
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
                new BigDecimal("35"), true, false, "报价", "备注", null, null);

        QuoteSheetResponse response = store().update(9L, headerOnly, 3L);

        assertThat(response.name()).isEqualTo("改名后的报单");
        assertThat(response.lengthPremium()).isEqualByComparingTo("35");
        assertThat(response.brands()).hasSize(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).prices()).hasSize(1);
    }

    /**
     * 回归(并发保护): 行级写必须显式对父单据加 {@code OPTIMISTIC_FORCE_INCREMENT} 锁。
     * <p>覆盖边界: 本仓库未引入 H2/Testcontainers, 无法真实执行 PostgreSQL 版本推进,
     * 故用 mock {@link EntityManager} 模拟 Hibernate 在 force increment 时自增版本,
     * 仅验证调用契约与返回 version 递增, 不验证真实数据库行版本变化。
     */
    @Test
    void addItem_forcesParentVersionIncrement() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(5L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L);
        stubForceIncrement();

        QuoteSheetItemWrite added = store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE, List.of()), 5L);

        assertThat(added.version()).isEqualTo(6L);
        verify(entityManager).lock(existing, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void updateItem_forcesParentVersionIncrement() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(5L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        stubForceIncrement();

        QuoteSheetItemWrite updated = store().updateItem(9L, 301L,
                new QuoteSheetRequest.ItemRequest("高线", "HPB300", 10, "12米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3400"), null))), 5L);

        assertThat(updated.version()).isEqualTo(6L);
        verify(entityManager).lock(existing, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void deleteItem_forcesParentVersionIncrement() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(5L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        stubForceIncrement();

        Long version = store().deleteItem(9L, 301L, 5L);

        assertThat(version).isEqualTo(6L);
        verify(entityManager).lock(existing, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    /** 表头-only 更新由 {@code @Version} 自然递增一次, 不得再走 FORCE_INCREMENT, 避免重复自增。 */
    @Test
    void update_headerOnly_doesNotForceIncrement() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        store().update(9L, headerOnlyRequest(), 3L);

        verifyNoInteractions(entityManager);
    }

    private void stubForceIncrement() {
        doAnswer(invocation -> {
            QuoteSheet sheet = invocation.getArgument(0);
            sheet.setVersion(sheet.getVersion() + 1);
            return null;
        }).when(entityManager).lock(any(QuoteSheet.class), eq(LockModeType.OPTIMISTIC_FORCE_INCREMENT));
    }

    private QuoteSheetRequest headerOnlyRequest() {
        return new QuoteSheetRequest(
                "改名后的报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("35"), true, false, "报价", "备注", null, null);
    }

    @Test
    void addItem_appendsWithNextLineNoAndResolvesSupplier() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L, 888L);
        when(supplierQuery.findActiveNormalById(77L))
                .thenReturn(Optional.of(new SupplierQuery.SupplierSnapshot(
                        77L, "S001", "杭州物资有限公司", "杭州物资")));

        QuoteSheetItemWrite added = store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", new BigDecimal("5"),
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3300"), 77L))),
                1L);

        assertThat(added.item().id()).isEqualTo(777L);
        assertThat(added.item().lineNo()).isEqualTo(2);
        assertThat(added.item().prices().get(0).supplierName()).isEqualTo("杭州物资");
        assertThat(added.version()).isEqualTo(1L);
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
                .hasMessageContaining("版本已变更");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateItem_replacesFieldsAndPrices() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(4L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetItemWrite updated = store().updateItem(9L, 301L,
                new QuoteSheetRequest.ItemRequest("高线", "HPB300", 10, "12米", new BigDecimal("2.5"),
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3400"), null))),
                4L);

        assertThat(updated.item().category()).isEqualTo("高线");
        assertThat(updated.item().ton()).isEqualByComparingTo("2.5");
        assertThat(updated.item().prices()).hasSize(1);
        assertThat(updated.item().prices().get(0).brandName()).isEqualTo("中天");
        assertThat(updated.version()).isEqualTo(4L);
    }

    /** 行级写: 现货价品牌不属于本单品牌列表时 422, 不得落库触发唯一键。 */
    @Test
    void addItem_rejectsPriceBrandNotInSheetBrands() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("亚新", new BigDecimal("3300"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 行级写: 同一行 prices 内 trim 后重名时 422, 不得落库触发唯一键。 */
    @Test
    void addItem_rejectsDuplicatePriceBrandWithinRow() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3300"), null),
                                new QuoteSheetRequest.ItemPriceRequest(" 中天 ", new BigDecimal("3310"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌重复")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 行级写: 整行替换时现货价品牌不属于本单品牌列表时 422。 */
    @Test
    void updateItem_rejectsPriceBrandNotInSheetBrands() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().updateItem(9L, 301L,
                new QuoteSheetRequest.ItemRequest("高线", "HPB300", 10, "12米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("亚新", new BigDecimal("3400"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 行级写: 整行替换时同一行 prices trim 后重名时 422。 */
    @Test
    void updateItem_rejectsDuplicatePriceBrandWithinRow() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().updateItem(9L, 301L,
                new QuoteSheetRequest.ItemRequest("高线", "HPB300", 10, "12米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3400"), null),
                                new QuoteSheetRequest.ItemPriceRequest(" 中天 ", new BigDecimal("3410"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌重复")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
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

        Long version = store().deleteItem(9L, 301L, 1L);

        assertThat(version).isEqualTo(1L);
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
        when(supplierQuery.findActiveNormalById(77L))
                .thenReturn(Optional.of(new SupplierQuery.SupplierSnapshot(
                        77L, "S001", "杭州物资有限公司", "杭州物资")));

        QuoteSheetResponse response = store().create(requestWithSupplier(77L));

        QuoteSheetResponse.ItemPriceResponse price = response.items().get(0).prices().get(0);
        assertThat(price.supplierId()).isEqualTo(77L);
        assertThat(price.supplierName()).isEqualTo("杭州物资");
    }

    @Test
    void create_rejectsUnknownSupplier() {
        when(supplierQuery.findActiveNormalById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store().create(requestWithSupplier(404L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在");
        verify(repository, never()).saveAndFlush(any());
    }

    /**
     * 回归: 同一单据连续两次保存相同品牌 + 相同 line_no 明细, 必须复用原品牌/明细/现货价实体,
     * 雪花 id 保持不变且不再分配新 id。
     * <p>旧实现 clear + 重新 add 会产生新子实体 id, 生产上触发
     * {@code uk_quote_sheet_brand(sheet_id, brand_name)}、{@code uk_quote_item_line(sheet_id, line_no)}、
     * {@code uk_quote_item_price(item_id, brand_name)} 冲突并映射为 409。
     */
    @Test
    void update_sameBrandAndLineNoTwice_reusesChildEntityIds() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetResponse first = store().update(9L, request(), null);
        QuoteSheetResponse second = store().update(9L, request(), null);

        assertThat(first.brands()).hasSize(1);
        assertThat(second.items()).hasSize(1);
        assertThat(existing.getBrands()).hasSize(1);
        assertThat(existing.getBrands().get(0).getId()).isEqualTo(201L);
        assertThat(existing.getItems()).hasSize(1);
        assertThat(existing.getItems().get(0).getId()).isEqualTo(301L);
        assertThat(existing.getItems().get(0).getPrices()).hasSize(1);
        assertThat(existing.getItems().get(0).getPrices().get(0).getId()).isEqualTo(401L);
        verify(snowflakeIdGenerator, never()).nextId();
    }

    /** 回归: line_no 命中复用明细实体, 品牌价按 brandName 协调, 仅新建真正新增的子实体。 */
    @Test
    void update_reconcilesItemsByLineNoAndPricesByBrand() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));
        when(snowflakeIdGenerator.nextId()).thenReturn(555L, 666L);

        QuoteSheetResponse response = store().update(9L, new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("沙钢", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("沙钢", new BigDecimal("3280"), null))))), null);

        assertThat(response.items()).hasSize(1);
        assertThat(existing.getItems().get(0).getId()).isEqualTo(301L);
        assertThat(existing.getItems().get(0).getPrices().get(0).getId()).isEqualTo(666L);
        assertThat(existing.getItems().get(0).getPrices().get(0).getBrandName()).isEqualTo("沙钢");
        assertThat(existing.getBrands()).extracting(QuoteSheetBrand::getBrandName).containsExactly("沙钢");
        assertThat(existing.getBrands().get(0).getId()).isEqualTo(555L);
    }

    /** 表头-only PUT: 必须能更新 specQuantityLocked, 且不影响另一个 locked 参照锁。 */
    @Test
    void update_headerOnly_updatesSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(3L);
        existing.setLocked(true);
        existing.setSpecQuantityLocked(false);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest headerOnly = new QuoteSheetRequest(
                "改名后的报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("35"), true, true, "报价", "备注", null, null);

        QuoteSheetResponse response = store().update(9L, headerOnly, 3L);

        assertThat(response.specQuantityLocked()).isTrue();
        assertThat(response.locked()).isTrue();
        assertThat(existing.isSpecQuantityLocked()).isTrue();
        verifyNoInteractions(entityManager);
    }

    /** 整体替换: specQuantityLocked 随请求持久化, 不得丢失。 */
    @Test
    void update_fullReplace_persistsSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(false);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest base = request();
        QuoteSheetRequest locked = new QuoteSheetRequest(
                base.name(), base.projectId(), base.projectName(), base.orderDate(), base.refDate(), base.refPeriod(),
                base.lengthPremium(), base.locked(), true, base.status(), base.remark(), base.brands(), base.items());

        QuoteSheetResponse response = store().update(9L, locked, 1L);

        assertThat(response.specQuantityLocked()).isTrue();
        assertThat(existing.isSpecQuantityLocked()).isTrue();
    }

    /** 行级写只动明细, 不得清零表头 specQuantityLocked。 */
    @Test
    void addItem_doesNotClearSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(5L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L);
        stubForceIncrement();

        store().addItem(9L, new QuoteSheetRequest.ItemRequest(
                "盘螺", "HRB400E", 8, "9米", BigDecimal.ONE, List.of()), 5L);

        assertThat(existing.isSpecQuantityLocked()).isTrue();
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
                new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", new BigDecimal("10"),
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), supplierId)))));
    }

    private QuoteSheetRequest request() {
        return new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", new BigDecimal("10"),
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));
    }
}
