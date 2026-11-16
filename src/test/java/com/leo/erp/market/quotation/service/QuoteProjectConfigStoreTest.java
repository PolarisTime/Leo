package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.lengthpremium.service.LengthPremiumService;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.ProjectQuery.ProjectQuoteSettings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 比价项目配置读写的归并口径（V220）。
 *
 * <p>锁四件事：</p>
 * <ol>
 *   <li><b>读</b>：响应形状不变，但品牌/品种/商品/加价/代取/指定品牌全部来自项目资料，
 *       只有运费与备注来自比价配置表；未配置运费时 version=0 且不落库；</li>
 *   <li><b>写</b>：只写运费与备注，请求里的旧字段（products/lengthPremium/hrb400eFallback/
 *       designatedBrands、brands[].categories/sortOrder）一律忽略；</li>
 *   <li><b>越界</b>：品牌必须在项目 allowed_brands 内 → 422；清单为空 = 不限制；</li>
 *   <li><b>并发</b>：版本不匹配 → 412；仅改品牌子集合时父版本仍要递增（FORCE_INCREMENT）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class QuoteProjectConfigStoreTest {

    private static final long PROJECT_ID = 88L;

    @Mock
    private QuoteProjectConfigRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private LengthPremiumService lengthPremiumService;

    private QuoteProjectConfigStore store() {
        return new QuoteProjectConfigStore(
                repository, snowflakeIdGenerator, entityManager, projectQuery, lengthPremiumService);
    }

    // ------------------------------------------------------------------
    // 读：按项目资料投影
    // ------------------------------------------------------------------

    @Test
    void find_shouldDeriveBrandsAndScalarsFromProjectMasterData() {
        givenSettings(new ProjectQuoteSettings(PROJECT_ID,
                List.of("中天", "萍钢"),
                Map.of("中天", List.of("直条", "盘螺")),
                List.of("螺纹钢|HRB400E|10|9米"),
                new BigDecimal("28.00"),
                true,
                List.of("中天")));
        givenConfig(config(brand("中天", "12.50"), brand("萍钢", "8.00")));
        givenQuoteSource("MYSTEEL");

        QuoteProjectConfigResponse response = store().find(PROJECT_ID);

        // 品牌与顺序来自 allowed_brands，运费来自比价配置表，品种来自项目资料
        assertThat(response.brands()).extracting(QuoteProjectConfigResponse.BrandResponse::brandName)
                .containsExactly("中天", "萍钢");
        assertThat(response.brands().get(0).freight()).isEqualByComparingTo("12.50");
        assertThat(response.brands().get(0).categories()).containsExactly("直条", "盘螺");
        assertThat(response.brands().get(1).categories()).isEmpty();
        assertThat(response.brands()).extracting(QuoteProjectConfigResponse.BrandResponse::sortOrder)
                .containsExactly(0, 1);

        assertThat(response.lengthPremium()).isEqualByComparingTo("28.00");
        assertThat(response.hrb400eFallback()).isTrue();
        assertThat(response.products()).containsExactly("螺纹钢|HRB400E|10|9米");
        assertThat(response.designatedBrands()).containsExactly("中天");
        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.quoteSource()).isEqualTo("MYSTEEL");
    }

    @Test
    void find_whenNoFreightConfigured_shouldStillListProjectBrandsWithZeroFreight() {
        givenSettings(new ProjectQuoteSettings(PROJECT_ID,
                List.of("中天"), Map.of(), List.of(), null, false, List.of()));
        when(repository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.empty());

        QuoteProjectConfigResponse response = store().find(PROJECT_ID);

        assertThat(response.brands()).hasSize(1);
        assertThat(response.brands().get(0).brandName()).isEqualTo("中天");
        assertThat(response.brands().get(0).freight()).isEqualByComparingTo("0");
        assertThat(response.version()).isEqualTo(0L);
        assertThat(response.remark()).isNull();
    }

    @Test
    void find_shouldFallBackToLengthPremiumMasterDataWhenProjectDoesNotConfigureIt() {
        givenSettings(new ProjectQuoteSettings(PROJECT_ID,
                List.of(), Map.of(), List.of(), null, false, List.of()));
        when(repository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.empty());
        when(lengthPremiumService.activePremiumMap()).thenReturn(Map.of("12米", 35));

        assertThat(store().find(PROJECT_ID).lengthPremium()).isEqualByComparingTo("35");
    }

    @Test
    void find_whenProjectMissing_shouldReturnEmptyBrandsWithDefaultPremium() {
        when(projectQuery.findQuoteSettingsById(PROJECT_ID)).thenReturn(Optional.empty());
        when(repository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.empty());
        when(lengthPremiumService.activePremiumMap()).thenReturn(Map.of());

        QuoteProjectConfigResponse response = store().find(PROJECT_ID);

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.brands()).isEmpty();
        assertThat(response.lengthPremium()).isEqualByComparingTo("30");
        assertThat(response.products()).isEmpty();
        assertThat(response.designatedBrands()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 写：只写运费 + 备注
    // ------------------------------------------------------------------

    @Test
    void save_shouldWriteOnlyFreightAndRemarkAndIgnoreLegacyFields() {
        givenSettings(new ProjectQuoteSettings(PROJECT_ID,
                List.of("中天"),
                Map.of("中天", List.of("直条")),
                List.of("螺纹钢|HRB400E|10|9米"),
                new BigDecimal("28.00"),
                true,
                List.of("中天")));
        QuoteProjectConfig existing = config(brand("中天", "1.00"));
        existing.setProducts("旧商品键");
        existing.setLengthPremium(new BigDecimal("31.00"));
        existing.setDesignatedBrands("旧指定品牌");
        existing.setHrb400eFallback(false);
        when(repository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 请求里故意塞旧字段 + categories/sortOrder：它们必须被忽略
        QuoteProjectConfigRequest request = new QuoteProjectConfigRequest(
                new BigDecimal("99.00"), true, List.of("伪造商品"), List.of("伪造品牌"), "新备注",
                List.of(new QuoteProjectConfigRequest.BrandRequest(
                        "中天", new BigDecimal("15.00"), List.of("伪造品种"), 7)));

        QuoteProjectConfigResponse response = store().save(PROJECT_ID, request, 3L);

        // 运费与备注写进去
        assertThat(existing.getBrands()).hasSize(1);
        assertThat(existing.getBrands().get(0).getFreight()).isEqualByComparingTo("15.00");
        assertThat(existing.getBrands().get(0).getSortOrder()).isZero();
        assertThat(existing.getRemark()).isEqualTo("新备注");
        // 旧标量字段与品种一律不动（仍保留迁移前/迁移时写入的原值）
        assertThat(existing.getProducts()).isEqualTo("旧商品键");
        assertThat(existing.getLengthPremium()).isEqualByComparingTo("31.00");
        assertThat(existing.getDesignatedBrands()).isEqualTo("旧指定品牌");
        assertThat(existing.isHrb400eFallback()).isFalse();
        assertThat(existing.getBrands().get(0).getCategories()).isNull();
        // 响应仍按项目资料 + 新运费派生
        assertThat(response.brands().get(0).freight()).isEqualByComparingTo("15.00");
        assertThat(response.brands().get(0).categories()).containsExactly("直条");
        assertThat(response.lengthPremium()).isEqualByComparingTo("28.00");
        assertThat(response.remark()).isEqualTo("新备注");
    }

    @Test
    void save_whenBrandOutsideProjectAllowedBrands_shouldReject422() {
        givenSettings(new ProjectQuoteSettings(PROJECT_ID,
                List.of("中天"), Map.of(), List.of(), null, false, List.of()));
        when(repository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.empty());

        QuoteProjectConfigRequest request = requestWithBrands("中天", "萍钢");

        assertThatThrownBy(() -> store().save(PROJECT_ID, request, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getMessage()).contains("萍钢").contains("项目资料");
                });
        verify(repository, never()).saveAndFlush(any(QuoteProjectConfig.class));
    }

    @Test
    void save_whenProjectHasNoAllowedBrands_shouldAllowAnyBrand() {
        givenSettings(new ProjectQuoteSettings(PROJECT_ID,
                List.of(), Map.of(), List.of(), null, false, List.of()));
        when(repository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.empty());
        when(snowflakeIdGenerator.nextId()).thenReturn(1L, 2L);
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuoteProjectConfigResponse response = store().save(PROJECT_ID, requestWithBrands("任意品牌"), null);

        // 清单为空 = 不限制：品牌可写，但响应里的 brands 仍按项目资料（空）投影
        assertThat(response.brands()).isEmpty();
    }

    @Test
    void save_withStaleVersion_shouldReject412() {
        QuoteProjectConfig existing = config(brand("中天", "1.00"));
        existing.setVersion(5L);
        when(repository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> store().save(PROJECT_ID, requestWithBrands("中天"), 4L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRECONDITION_FAILED));
    }

    @Test
    void save_whenOnlyBrandsChanged_shouldForceIncrementVersion() {
        givenSettings(new ProjectQuoteSettings(PROJECT_ID,
                List.of("中天"), Map.of(), List.of(), null, false, List.of()));
        QuoteProjectConfig existing = config(brand("中天", "1.00"));
        when(repository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 备注与已存一致（都为空）→ 父行不会被自然标脏，必须显式 FORCE_INCREMENT
        store().save(PROJECT_ID, requestWithBrands("中天"), null);

        verify(entityManager).lock(existing, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void givenSettings(ProjectQuoteSettings settings) {
        lenient().when(projectQuery.findQuoteSettingsById(PROJECT_ID)).thenReturn(Optional.of(settings));
    }

    private void givenConfig(QuoteProjectConfig config) {
        lenient().when(repository.findByProjectIdAndDeletedFlagFalse(PROJECT_ID))
                .thenReturn(Optional.of(config));
    }

    private void givenQuoteSource(String quoteSource) {
        lenient().when(projectQuery.findActiveById(PROJECT_ID)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(PROJECT_ID, "测试项目", "测项",
                        null, "C001", null, null, quoteSource)));
    }

    private static QuoteProjectConfig config(QuoteProjectBrand... brands) {
        QuoteProjectConfig config = new QuoteProjectConfig();
        config.setId(1L);
        config.setProjectId(PROJECT_ID);
        config.setVersion(3L);
        config.setLengthPremium(new BigDecimal("30.00"));
        config.setBrands(new ArrayList<>(Arrays.asList(brands)));
        for (QuoteProjectBrand brand : brands) {
            brand.setConfig(config);
        }
        return config;
    }

    private static QuoteProjectBrand brand(String name, String freight) {
        QuoteProjectBrand brand = new QuoteProjectBrand();
        brand.setId(10L + Math.abs(name.hashCode() % 1000));
        brand.setBrandName(name);
        brand.setFreight(new BigDecimal(freight));
        return brand;
    }

    private static QuoteProjectConfigRequest requestWithBrands(String... brandNames) {
        List<QuoteProjectConfigRequest.BrandRequest> brands = Arrays.stream(brandNames)
                .map(name -> new QuoteProjectConfigRequest.BrandRequest(
                        name, new BigDecimal("5.00"), List.of(), 0))
                .toList();
        // 旧字段保持有值，验证「忽略」而不是「报错」
        return new QuoteProjectConfigRequest(
                new BigDecimal("40.00"), true,
                List.of("旧商品"), List.of("旧指定品牌"), null, brands);
    }

    private static ProjectQuoteSettings settingsOf(List<String> allowedBrands) {
        return new ProjectQuoteSettings(PROJECT_ID, allowedBrands, new LinkedHashMap<>(),
                List.of(), null, false, List.of());
    }
}
