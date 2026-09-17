package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
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
import static org.mockito.Mockito.atLeastOnce;
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
    private QuoteProjectConfigRepository quoteProjectConfigRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private EntityManager entityManager;

    private QuoteSheetStore store() {
        return new QuoteSheetStore(repository, quoteProjectConfigRepository, snowflakeIdGenerator,
                supplierQuery, entityManager);
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

    /** 有效集合: projectId 为 null 时不查询项目配置, 仅按单据/请求品牌校验, 未知品牌仍 422。 */
    @Test
    void addItem_projectIdNull_doesNotConsultProjectConfig() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("铜陵富鑫", new BigDecimal("3300"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中");
        verifyNoInteractions(quoteProjectConfigRepository);
    }

    /** 有效集合: 项目配置存在且新增品牌时, 行级写该品牌价格必须放行, 并把品牌补齐到单据快照。 */
    @Test
    void addItem_configOnlyBrand_isAcceptedAndSnapshotSynced() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setProjectId(77L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands("中天", "铜陵富鑫")));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L, 888L);

        QuoteSheetItemWrite added = store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("铜陵富鑫", new BigDecimal("3300"), null))), 1L);

        assertThat(added.item().prices()).extracting(QuoteSheetResponse.ItemPriceResponse::brandName)
                .containsExactly("铜陵富鑫");
        assertThat(existing.getBrands()).extracting(QuoteSheetBrand::getBrandName)
                .containsExactly("中天", "铜陵富鑫");
        verify(quoteProjectConfigRepository, atLeastOnce()).findByProjectIdAndDeletedFlagFalse(77L);
    }

    /** 有效集合: 配置品牌名前后空格在比对前 trim, 命中已有单据品牌名。 */
    @Test
    void addItem_configBrandIsTrimmedBeforeMatching() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setProjectId(77L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands(" 铜陵富鑫 ")));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L);

        QuoteSheetItemWrite added = store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("铜陵富鑫", new BigDecimal("3300"), null))), 1L);

        assertThat(added.item().prices()).extracting(QuoteSheetResponse.ItemPriceResponse::brandName)
                .containsExactly("铜陵富鑫");
        assertThat(existing.getBrands()).extracting(QuoteSheetBrand::getBrandName)
                .contains("铜陵富鑫");
    }

    /** 有效集合: 配置不存在(未配置项目)时, 仍只按单据快照校验, 配置独有品牌不可用。 */
    @Test
    void addItem_configMissing_onlySheetBrandsAllowed() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setProjectId(77L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("铜陵富鑫", new BigDecimal("3300"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中");
        verify(repository, never()).saveAndFlush(any());
    }

    /** 有效集合: 配置存在但品牌集合为空时, 仍只按单据快照校验。 */
    @Test
    void addItem_configWithEmptyBrands_onlySheetBrandsAllowed() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setProjectId(77L);
        existing.setVersion(1L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands()));

        assertThatThrownBy(() -> store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("铜陵富鑫", new BigDecimal("3300"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中");
    }

    /** 有效集合: 创建单据时同样按"请求品牌 ∪ 配置品牌"放行配置新增品牌。 */
    @Test
    void create_configOnlyBrand_isAcceptedAndSnapshotSynced() {
        when(snowflakeIdGenerator.nextId()).thenReturn(100L, 201L, 301L, 302L, 303L);
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands("中天", "铜陵富鑫")));

        QuoteSheetRequest request = new QuoteSheetRequest(
                "9月9日报单", 77L, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("铜陵富鑫", new BigDecimal("3280"), null)))));

        QuoteSheetResponse response = store().create(request);

        assertThat(response.items().get(0).prices()).extracting(QuoteSheetResponse.ItemPriceResponse::brandName)
                .containsExactly("铜陵富鑫");
        assertThat(response.brands()).extracting(QuoteSheetResponse.BrandResponse::brandName)
                .containsExactly("中天", "铜陵富鑫");
    }

    /** 有效集合: 整单替换同样按"请求品牌 ∪ 配置品牌"放行配置新增品牌, 父版本恰好 +1。 */
    @Test
    void update_configOnlyBrand_isAcceptedAndForcesParentVersionIncrement() {
        QuoteSheet existing = sheetWithItemMatchingFullRequestHeader(9L);
        existing.setProjectId(77L);
        existing.setVersion(5L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands("中天", "铜陵富鑫")));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L);
        stubForceIncrement();

        QuoteSheetRequest request = new QuoteSheetRequest(
                "9月9日报单", 77L, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("铜陵富鑫", new BigDecimal("3280"), null)))));

        QuoteSheetResponse response = store().update(9L, request, 5L);

        assertThat(response.version()).isEqualTo(6L);
        assertThat(response.brands()).extracting(QuoteSheetResponse.BrandResponse::brandName)
                .containsExactly("中天", "铜陵富鑫");
        verify(entityManager).lock(existing, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    /** 快照全量协调: 表头-only 写把快照对齐配置(含删除配置已移除的品牌), 且父版本恰好 +1。 */
    @Test
    void update_headerOnly_configOnlyBrand_syncsSnapshotAndIncrementsVersionOnce() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setProjectId(77L);
        existing.setVersion(3L);
        existing.setName("9月9日报单");
        existing.setProjectName("云潮筝鸣府");
        existing.setOrderDate(LocalDate.of(2026, 9, 9));
        existing.setRefDate(LocalDate.of(2026, 9, 10));
        existing.setRefPeriod("9:30 上午");
        existing.setLengthPremium(new BigDecimal("30"));
        existing.setStatus("报价");
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands("铜陵富鑫")));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L);
        stubForceIncrement();

        QuoteSheetRequest headerOnly = new QuoteSheetRequest(
                "9月9日报单", 77L, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null, null, null);

        QuoteSheetResponse response = store().update(9L, headerOnly, 3L);

        assertThat(response.version()).isEqualTo(4L);
        assertThat(response.brands()).extracting(QuoteSheetResponse.BrandResponse::brandName)
                .containsExactly("铜陵富鑫");
    }

    /** 漂移消除: 快照已与配置一致时不再改动集合, 也不产生额外版本自增。 */
    @Test
    void update_snapshotAlreadyInSync_doesNotForceIncrementWhenHeaderUnchanged() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setProjectId(77L);
        existing.setVersion(3L);
        existing.setName("9月9日报单");
        existing.setProjectName("云潮筝鸣府");
        existing.setOrderDate(LocalDate.of(2026, 9, 9));
        existing.setRefDate(LocalDate.of(2026, 9, 10));
        existing.setRefPeriod("9:30 上午");
        existing.setLengthPremium(new BigDecimal("30"));
        existing.setStatus("报价");
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands("中天")));

        QuoteSheetRequest headerOnly = new QuoteSheetRequest(
                "9月9日报单", 77L, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null, null, null);

        store().update(9L, headerOnly, 3L);

        verifyNoInteractions(entityManager);
    }

    /** 真源为配置: 配置已删除的品牌即使仍在单据快照中也必须 422(替换上一版并集放行)。 */
    @Test
    void addItem_configDeletedBrand_isRejected422() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setProjectId(77L);
        existing.setVersion(1L);
        existing.getBrands().add(extraBrand(existing, 999L, "铜陵富鑫"));
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands("中天")));

        assertThatThrownBy(() -> store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("铜陵富鑫", new BigDecimal("3300"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 真源为配置: 快照中残留、配置已无的品牌随下一次成功写的快照同步被移除, 与配置完全对齐。 */
    @Test
    void addItem_staleSnapshotBrand_removedByConfigSync() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setProjectId(77L);
        existing.setVersion(1L);
        existing.getBrands().add(extraBrand(existing, 999L, "铜陵富鑫"));
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands("中天", "沙钢")));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L, 888L, 889L);

        QuoteSheetItemWrite added = store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("沙钢", new BigDecimal("3300"), null))), 1L);

        assertThat(added.item().prices()).extracting(QuoteSheetResponse.ItemPriceResponse::brandName)
                .containsExactly("沙钢");
        assertThat(existing.getBrands()).extracting(QuoteSheetBrand::getBrandName)
                .containsExactly("中天", "沙钢");
    }

    /** 整单替换: 快照与配置完全对齐(删除多余、补齐缺失), 仅子集合变更时父版本恰好 +1。 */
    @Test
    void update_fullReplace_alignsSnapshotToConfig() {
        QuoteSheet existing = sheetWithItemMatchingFullRequestHeader(9L);
        existing.setProjectId(77L);
        existing.setVersion(5L);
        existing.getBrands().add(extraBrand(existing, 999L, "铜陵富鑫"));
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(77L))
                .thenReturn(Optional.of(configWithBrands("中天", "沙钢")));
        when(snowflakeIdGenerator.nextId()).thenReturn(777L, 888L, 889L);
        stubForceIncrement();

        QuoteSheetRequest request = new QuoteSheetRequest(
                "9月9日报单", 77L, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("铜陵富鑫", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("沙钢", new BigDecimal("3280"), null)))));

        QuoteSheetResponse response = store().update(9L, request, 5L);

        assertThat(response.brands()).extracting(QuoteSheetResponse.BrandResponse::brandName)
                .containsExactly("中天", "沙钢");
        assertThat(response.version()).isEqualTo(6L);
    }

    private QuoteSheetBrand extraBrand(QuoteSheet sheet, Long id, String name) {
        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(id);
        brand.setSheet(sheet);
        brand.setBrandName(name);
        brand.setFreight(new BigDecimal("30"));
        brand.setSortOrder(1);
        return brand;
    }

    private QuoteProjectConfig configWithBrands(String... brandNames) {
        QuoteProjectConfig config = new QuoteProjectConfig();
        config.setId(500L);
        config.setProjectId(77L);
        config.setLengthPremium(new BigDecimal("30"));
        int index = 0;
        for (String brandName : brandNames) {
            QuoteProjectBrand brand = new QuoteProjectBrand();
            brand.setId(600L + index);
            brand.setConfig(config);
            brand.setBrandName(brandName);
            // 与 sheetWithItem 的品牌运费(30)一致, 便于"快照已同步"用例断言无差异。
            brand.setFreight(new BigDecimal("30"));
            brand.setSortOrder(index);
            config.getBrands().add(brand);
            index += 1;
        }
        return config;
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

    /**
     * P2 回归: 表头-only PATCH 未显式携带 locked/specQuantityLocked(null) 时必须保留原值,
     * 不得被静默清零(仅显式 false 才解锁)。
     */
    @Test
    void update_headerOnly_nullLockFlags_preserveExistingValues() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(3L);
        existing.setLocked(true);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest headerOnly = new QuoteSheetRequest(
                "改名后的报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("35"), null, null, "报价", "备注", null, null);

        QuoteSheetResponse response = store().update(9L, headerOnly, 3L);

        assertThat(response.locked()).isTrue();
        assertThat(response.specQuantityLocked()).isTrue();
        assertThat(existing.isLocked()).isTrue();
        assertThat(existing.isSpecQuantityLocked()).isTrue();
        verifyNoInteractions(entityManager);
    }

    /** 表头-only 显式 false 才是解锁; 同时把两个锁标志都置 false。 */
    @Test
    void update_headerOnly_explicitFalse_releasesLockFlags() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(3L);
        existing.setLocked(true);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest headerOnly = new QuoteSheetRequest(
                "改名后的报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("35"), false, false, "报价", "备注", null, null);

        QuoteSheetResponse response = store().update(9L, headerOnly, 3L);

        assertThat(response.locked()).isFalse();
        assertThat(response.specQuantityLocked()).isFalse();
        assertThat(existing.isLocked()).isFalse();
        assertThat(existing.isSpecQuantityLocked()).isFalse();
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

    /** 行级写(仅改现货价)不得清零表头 specQuantityLocked。 */
    @Test
    void updateItem_doesNotClearSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(5L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        stubForceIncrement();

        store().updateItem(9L, 301L, new QuoteSheetRequest.ItemRequest(
                "螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3400"), null))), 5L);

        assertThat(existing.isSpecQuantityLocked()).isTrue();
    }

    /**
     * P1-1 回归: 整体替换(brands/items 任一非 null)必须显式对父单据加
     * {@code OPTIMISTIC_FORCE_INCREMENT}, 保证仅改子集合(如某现货价)时父版本仍恰好 +1。
     */
    @Test
    void update_fullReplace_forcesParentVersionIncrement() {
        QuoteSheet existing = sheetWithItemMatchingFullRequestHeader(9L);
        existing.setVersion(5L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        stubForceIncrement();

        QuoteSheetResponse response = store().update(9L, fullRequest(12, BigDecimal.TEN, "3600"), 5L);

        assertThat(response.version()).isEqualTo(6L);
        assertThat(response.items().get(0).prices().get(0).spotPrice()).isEqualByComparingTo("3600");
        verify(entityManager).lock(existing, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    /**
     * P1-2 回归: 整体替换同时变更表头标量时, 父行会被自然标脏并由 {@code @Version} 递增一次,
     * 不得再叠加 FORCE_INCREMENT(否则版本 +2)。此处 mock 环境下断言不再触发锁调用契约。
     */
    @Test
    void update_fullReplace_withHeaderChange_doesNotForceIncrement() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(5L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        store().update(9L, fullRequest(12, BigDecimal.TEN, "3600"), 5L);

        verifyNoInteractions(entityManager);
    }

    /** 行级写: 规格/数量已锁定时新增行(增加数量)必须 422。 */
    @Test
    void addItem_rejectsWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(5L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().addItem(9L,
                new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE, List.of()), 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 行级写: 规格/数量已锁定时删除行(减少数量)必须 422。 */
    @Test
    void deleteItem_rejectsWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(5L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().deleteItem(9L, 301L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
        assertThat(existing.getItems()).hasSize(1);
    }

    /** 行级写: 已锁定时改吨数(数量)必须 422。 */
    @Test
    void updateItem_rejectsQuantityChangeWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().updateItem(9L, 301L,
                new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 行级写: 已锁定时改规格(spec/length/category/material)必须 422。 */
    @Test
    void updateItem_rejectsSpecChangeWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().updateItem(9L, 301L,
                new QuoteSheetRequest.ItemRequest("高线", "HPB300", 10, "12米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null))), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 行级写: 已锁定时仅改现货价(不涉规格数量)仍允许。 */
    @Test
    void updateItem_allowsPriceOnlyChangeWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetItemWrite updated = store().updateItem(9L, 301L,
                new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3600"), null))), 1L);

        assertThat(updated.item().prices().get(0).spotPrice()).isEqualByComparingTo("3600");
        verify(repository).saveAndFlush(existing);
    }

    /** 整单替换: 已锁定时改规格必须 422, 且不得触碰父版本。 */
    @Test
    void update_fullReplace_rejectsSpecChangeWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(3L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().update(9L, fullRequest(10, BigDecimal.TEN, "3280", true), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(entityManager);
    }

    /** 整单替换: 已锁定时改吨数(数量)必须 422。 */
    @Test
    void update_fullReplace_rejectsQuantityChangeWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(3L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().update(9L, fullRequest(12, BigDecimal.ONE, "3280", true), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 整单替换: 已锁定时增行(行数变化)必须 422。 */
    @Test
    void update_fullReplace_rejectsRowCountChangeWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(3L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        QuoteSheetRequest twoRows = new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, true, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                                List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null))),
                        new QuoteSheetRequest.ItemRequest("盘螺", "HRB400E", 8, "9米", BigDecimal.ONE,
                                List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3300"), null)))));

        assertThatThrownBy(() -> store().update(9L, twoRows, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定");
        verify(repository, never()).saveAndFlush(any());
    }

    /** 整单替换: 已锁定时仅改现货价(不涉规格数量)仍允许, 且父版本 +1。 */
    @Test
    void update_fullReplace_allowsPriceOnlyChangeWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithItemMatchingFullRequestHeader(9L);
        existing.setVersion(7L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        stubForceIncrement();

        QuoteSheetResponse response = store().update(9L, fullRequest(12, BigDecimal.TEN, "3600", true), 7L);

        assertThat(response.version()).isEqualTo(8L);
        assertThat(response.items().get(0).prices().get(0).spotPrice()).isEqualByComparingTo("3600");
    }

    /**
     * P1-3 回归: 已删除中间行(行号空洞 {1,3})后整单改现货价(规格/数量不变)必须放行,
     * 行匹配按请求顺序而非理想行号 index+1, 不得误拒 422。
     */
    @Test
    void update_fullReplace_withLineNoHole_allowsPriceOnlyChangeWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithHoleItems(9L);
        existing.setVersion(4L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetResponse response = store().update(9L, holeRequest("3600", "3700", 8, BigDecimal.valueOf(5)), 4L);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).lineNo()).isEqualTo(1);
        assertThat(response.items().get(0).prices().get(0).spotPrice()).isEqualByComparingTo("3600");
        assertThat(response.items().get(1).lineNo()).isEqualTo(2);
        assertThat(response.items().get(1).prices().get(0).spotPrice()).isEqualByComparingTo("3700");
        verify(repository).saveAndFlush(existing);
    }

    /** P1-3 回归: 行号空洞下整单增行仍必须 422。 */
    @Test
    void update_fullReplace_withLineNoHole_rejectsAddedRowWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithHoleItems(9L);
        existing.setVersion(4L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        QuoteSheetRequest threeRows = new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, true, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(itemRequest("螺纹钢", "HRB400E", 12, "10", "3280"),
                        itemRequest("盘螺", "HRB400E", 8, "5", "3300"),
                        itemRequest("高线", "HPB300", 10, "1", "3400")));

        assertThatThrownBy(() -> store().update(9L, threeRows, 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定");
        verify(repository, never()).saveAndFlush(any());
    }

    /** P1-3 回归: 行号空洞下整单改规格仍必须 422。 */
    @Test
    void update_fullReplace_withLineNoHole_rejectsSpecChangeWhenSpecQuantityLocked() {
        QuoteSheet existing = sheetWithHoleItems(9L);
        existing.setVersion(4L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().update(9L, holeRequest("3600", "3700", 10, BigDecimal.valueOf(5)), 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定");
        verify(repository, never()).saveAndFlush(any());
    }

    /** 显式解锁(specQuantityLocked=false 的表头写)之后, 整单替换改规格才放行。 */
    @Test
    void update_unlockThenFullReplace_allowsSpecChange() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetResponse unlocked = store().update(9L, new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null, null, null), null);
        assertThat(unlocked.specQuantityLocked()).isFalse();

        QuoteSheetResponse replaced = store().update(9L, fullRequest(10, BigDecimal.TEN, "3280"), null);
        assertThat(replaced.items().get(0).spec()).isEqualTo(10);
    }

    /**
     * 缺陷 B 回归: 同一整单 PUT 内显式 {@code specQuantityLocked=false} 解锁并改规格/数量,
     * 校验必须按请求显式值放行(而非持久化旧值), 返回 200 且落库。
     */
    @Test
    void update_fullReplace_explicitUnlock_allowsSpecAndQuantityChangeInSamePut() {
        QuoteSheet existing = sheetWithItemMatchingFullRequestHeader(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetResponse response = store().update(9L, fullRequest(10, BigDecimal.ONE, "3280", false), 1L);

        assertThat(response.specQuantityLocked()).isFalse();
        assertThat(response.items().get(0).spec()).isEqualTo(10);
        assertThat(response.items().get(0).ton()).isEqualByComparingTo("1");
        assertThat(existing.isSpecQuantityLocked()).isFalse();
        assertThat(existing.getItems().get(0).getSpec()).isEqualTo(10);
        assertThat(existing.getItems().get(0).getTon()).isEqualByComparingTo("1");
    }

    /** 缺陷 B 回归: 同一 PUT 仅显式解锁(规格/数量不变)也必须 200 并落库解锁。 */
    @Test
    void update_fullReplace_explicitUnlockWithoutSpecChange_releasesLock() {
        QuoteSheet existing = sheetWithItemMatchingFullRequestHeader(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetResponse response = store().update(9L, fullRequest(12, BigDecimal.TEN, "3280", false), 1L);

        assertThat(response.specQuantityLocked()).isFalse();
        assertThat(response.items().get(0).spec()).isEqualTo(12);
        assertThat(existing.isSpecQuantityLocked()).isFalse();
    }

    /** 缺陷 B 回归: 未携带 specQuantityLocked(null) 时仍按持久化旧值拒绝改规格。 */
    @Test
    void update_fullReplace_omittedSpecQuantityLocked_stillRejectsSpecChange() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        QuoteSheetRequest omitted = new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, null, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 10, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));

        assertThatThrownBy(() -> store().update(9L, omitted, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 缺陷 B 回归: 持久化未锁定但请求显式 specQuantityLocked=true 时, 同请求改规格仍必须 422。 */
    @Test
    void update_fullReplace_explicitLockTrue_rejectsSpecChange() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(false);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().update(9L, fullRequest(10, BigDecimal.TEN, "3280", true), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格和数量已锁定")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    private QuoteSheetRequest fullRequest(Integer spec, BigDecimal ton, String spotPrice) {
        return fullRequest(spec, ton, spotPrice, false);
    }

    private QuoteSheetRequest fullRequest(Integer spec, BigDecimal ton, String spotPrice, boolean specQuantityLocked) {
        return new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, specQuantityLocked, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", spec, "9米", ton,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal(spotPrice), null)))));
    }

    /** 表头与 {@link #fullRequest} 完全一致的单据, 用于验证"仅子集合变更 → FORCE_INCREMENT"路径。 */
    private QuoteSheet sheetWithItemMatchingFullRequestHeader(Long id) {
        QuoteSheet sheet = sheetWithItem(id);
        sheet.setName("9月9日报单");
        sheet.setProjectName("云潮筝鸣府");
        sheet.setOrderDate(LocalDate.of(2026, 9, 9));
        sheet.setRefDate(LocalDate.of(2026, 9, 10));
        sheet.setRefPeriod("9:30 上午");
        sheet.setLengthPremium(new BigDecimal("30"));
        sheet.setLocked(false);
        sheet.setSpecQuantityLocked(false);
        sheet.setStatus("报价");
        return sheet;
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

    /** 含 line_no 空洞({1,3})、规格数量已锁定的单据, 表头与 {@link #holeRequest} 一致。 */
    private QuoteSheet sheetWithHoleItems(Long id) {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(id);
        sheet.setName("9月9日报单");
        sheet.setProjectName("云潮筝鸣府");
        sheet.setOrderDate(LocalDate.of(2026, 9, 9));
        sheet.setRefDate(LocalDate.of(2026, 9, 10));
        sheet.setRefPeriod("9:30 上午");
        sheet.setLengthPremium(new BigDecimal("30"));
        sheet.setLocked(false);
        sheet.setSpecQuantityLocked(true);
        sheet.setStatus("报价");
        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(201L);
        brand.setSheet(sheet);
        brand.setBrandName("中天");
        brand.setFreight(new BigDecimal("30"));
        brand.setSortOrder(0);
        sheet.getBrands().add(brand);
        sheet.getItems().add(holeItem(sheet, 301L, 1, "螺纹钢", "HRB400E", 12, "10", "3280"));
        sheet.getItems().add(holeItem(sheet, 303L, 3, "盘螺", "HRB400E", 8, "5", "3300"));
        return sheet;
    }

    private QuoteSheetItem holeItem(QuoteSheet sheet, Long id, int lineNo, String category,
                                    String material, int spec, String ton, String spotPrice) {
        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(id);
        item.setSheet(sheet);
        item.setLineNo(lineNo);
        item.setCategory(category);
        item.setMaterial(material);
        item.setSpec(spec);
        item.setLength("9米");
        item.setTon(new BigDecimal(ton));
        QuoteSheetItemPrice price = new QuoteSheetItemPrice();
        price.setId(id + 10000);
        price.setItem(item);
        price.setBrandName("中天");
        price.setSpotPrice(new BigDecimal(spotPrice));
        item.getPrices().add(price);
        return item;
    }

    private QuoteSheetRequest holeRequest(String spotPrice1, String spotPrice2, int spec2, BigDecimal ton2) {
        return new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, true, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(itemRequest("螺纹钢", "HRB400E", 12, "10", spotPrice1),
                        itemRequest("盘螺", "HRB400E", spec2, ton2.toPlainString(), spotPrice2)));
    }

    private QuoteSheetRequest.ItemRequest itemRequest(String category, String material, int spec,
                                                      String ton, String spotPrice) {
        return new QuoteSheetRequest.ItemRequest(category, material, spec, "9米", new BigDecimal(ton),
                List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal(spotPrice), null)));
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
