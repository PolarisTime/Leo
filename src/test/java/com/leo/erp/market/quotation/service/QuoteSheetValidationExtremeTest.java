package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报价单校验极端输入单元测试。
 * <p>
 * 覆盖: 请求列表中的 {@code null} 元素(JSON 可合法反序列化为 {@code List} 中的 {@code null},
 * 而 Bean Validation 会跳过 null 元素, 因此必须在服务层显式拒绝)、同一请求内"解锁规格数量锁 + 改规格"
 * 的放行语义、行号空洞下的整单替换按请求顺序复用实体、表头无变化时不推进版本。
 */
@ExtendWith(MockitoExtension.class)
class QuoteSheetValidationExtremeTest {

    @Mock
    private QuoteSheetRepository repository;

    @Mock
    private QuoteProjectConfigRepository quoteProjectConfigRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private com.leo.erp.master.api.ProjectQuery projectQuery;

    @Mock
    private com.leo.erp.purchase.api.PurchaseOrderOptionQuery purchaseOrderOptionQuery;

    @Mock
    private EntityManager entityManager;

    private QuoteSheetStore store() {
        return new QuoteSheetStore(repository, quoteProjectConfigRepository, snowflakeIdGenerator,
                supplierQuery, projectQuery, purchaseOrderOptionQuery, entityManager);
    }

    /**
     * 回归: {@code brands:[null]} 可绕过 Bean Validation(跳过 null 元素), 必须在服务层 422 而不是 NPE/500。
     */
    @Test
    void create_nullBrandElement_rejectedAsValidationError() {
        QuoteSheetRequest request = request(
                Collections.singletonList(null),
                List.of(itemRequest("螺纹钢", "HRB400E", 12, "10", "3280")));

        assertThatThrownBy(() -> store().create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 回归: {@code items:[null]} 必须在服务层 422 而不是 NPE/500。 */
    @Test
    void create_nullItemElement_rejectedAsValidationError() {
        QuoteSheetRequest request = request(
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 0)),
                Collections.singletonList(null));

        assertThatThrownBy(() -> store().create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /** 回归: 同一行 {@code prices:[null]} 必须在服务层 422 而不是 NPE/500。 */
    @Test
    void create_nullPriceElement_rejectedAsValidationError() {
        QuoteSheetRequest request = request(
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        Collections.singletonList(null))));

        assertThatThrownBy(() -> store().create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    /**
     * 缺陷 B: 整单替换请求在同一请求内显式 {@code specQuantityLocked=false} 解锁并同时改规格,
     * 必须按"请求显式值"门控放行(200), 且落库解锁与新规格, 不再按持久化旧值误判 422。
     */
    @Test
    void update_fullReplace_sameRequestUnlockAndSpecChange_isAllowed() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setVersion(1L);
        existing.setSpecQuantityLocked(true);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest request = fullRequest(10, BigDecimal.TEN, "3280", false);
        QuoteSheetResponse response = store().update(9L, request, 1L);

        assertThat(response.specQuantityLocked()).isFalse();
        assertThat(response.items().get(0).spec()).isEqualTo(10);
        assertThat(existing.isSpecQuantityLocked()).isFalse();
        assertThat(existing.getItems().get(0).getSpec()).isEqualTo(10);
        verify(repository).saveAndFlush(any(QuoteSheet.class));
    }

    /**
     * 行号空洞 {2,3} 且请求 2 行时, 整单替换按"既有行 line_no 升序"与请求顺序一一对应复用实体,
     * 行号归一化为连续的 1,2; 原 id 必须保留(不得重建)。
     */
    @Test
    void update_fullReplace_lineNoHole_renumbersContiguouslyAndKeepsEntityIds() {
        QuoteSheet existing = new QuoteSheet();
        existing.setId(9L);
        existing.setSpecQuantityLocked(true);
        existing.setRefDate(LocalDate.of(2026, 9, 10));
        existing.setRefPeriod("9:30 上午");
        existing.getBrands().add(brand(existing, 201L, "中天"));
        existing.getItems().add(holeItem(existing, 301L, 2, "螺纹钢", "HRB400E", 12, "10", "3280"));
        existing.getItems().add(holeItem(existing, 303L, 3, "盘螺", "HRB400E", 8, "5", "3300"));
        existing.setVersion(4L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetResponse response = store().update(9L, holeRequest("3600", "3700", 8, BigDecimal.valueOf(5)), 4L);

        assertThat(response.items()).extracting(QuoteSheetResponse.ItemResponse::lineNo).containsExactly(1, 2);
        assertThat(response.items()).extracting(QuoteSheetResponse.ItemResponse::id).containsExactly(301L, 303L);
        assertThat(response.items().get(0).prices().get(0).spotPrice()).isEqualByComparingTo("3600");
        assertThat(response.items().get(1).prices().get(0).spotPrice()).isEqualByComparingTo("3700");
    }

    /**
     * 表头字段与库中完全一致且未携带子集合时, 表头-only 写不产生变更、不推进版本(仅回读权威版本)。
     */
    @Test
    void update_headerOnly_noOp_doesNotBumpVersion() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setName("9月9日报单");
        existing.setProjectName("云潮筝鸣府");
        existing.setOrderDate(LocalDate.of(2026, 9, 9));
        existing.setRefDate(LocalDate.of(2026, 9, 10));
        existing.setRefPeriod("9:30 上午");
        existing.setLengthPremium(new BigDecimal("30"));
        existing.setStatus("报价");
        existing.setVersion(5L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest request = new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null, null, null);
        QuoteSheetResponse response = store().update(9L, request, 5L);

        assertThat(response.version()).isEqualTo(5L);
        assertThat(existing.getVersion()).isEqualTo(5L);
    }

    /**
     * 参照锁 {@code locked} 与 {@code specQuantityLocked} 相互独立: 表头-only 切换规格数量锁
     * 不影响参照锁, 且不触碰参照日期/时段时放行。
     */
    @Test
    void update_specQuantityLockToggle_doesNotAffectRefLock() {
        QuoteSheet existing = sheetWithItem(9L);
        existing.setLocked(true);
        existing.setSpecQuantityLocked(false);
        existing.setRefDate(LocalDate.of(2026, 9, 10));
        existing.setRefPeriod("9:30 上午");
        existing.setVersion(2L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetRequest request = new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), null, true, "报价", null, null, null);
        QuoteSheetResponse response = store().update(9L, request, 2L);

        assertThat(response.specQuantityLocked()).isTrue();
        assertThat(response.locked()).isTrue();
    }

    private QuoteSheetRequest request(List<QuoteSheetRequest.BrandRequest> brands,
                                      List<QuoteSheetRequest.ItemRequest> items) {
        return new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, false, "报价", null, brands, items);
    }

    private QuoteSheetRequest fullRequest(Integer spec, BigDecimal ton, String spotPrice, boolean specQuantityLocked) {
        return new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, specQuantityLocked, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", spec, "9米", ton,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal(spotPrice), null)))));
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

    private QuoteSheet sheetWithItem(Long id) {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(id);
        sheet.getBrands().add(brand(sheet, 201L, "中天"));
        sheet.getItems().add(holeItem(sheet, 301L, 1, "螺纹钢", "HRB400E", 12, "10", "3280"));
        return sheet;
    }

    private QuoteSheetBrand brand(QuoteSheet sheet, Long id, String name) {
        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(id);
        brand.setSheet(sheet);
        brand.setBrandName(name);
        brand.setFreight(new BigDecimal("30"));
        brand.setSortOrder(0);
        return brand;
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
}
