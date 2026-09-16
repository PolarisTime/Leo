package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteProjectConfigStoreTest {

    @Mock
    private QuoteProjectConfigRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private EntityManager entityManager;

    private QuoteProjectConfigStore store() {
        return new QuoteProjectConfigStore(repository, snowflakeIdGenerator, entityManager);
    }

    @Test
    void find_returnsDefaultsWhenAbsent() {
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.empty());

        QuoteProjectConfigResponse response = store().find(88L);

        assertThat(response.projectId()).isEqualTo(88L);
        assertThat(response.lengthPremium()).isEqualByComparingTo("30");
        assertThat(response.hrb400eFallback()).isFalse();
        assertThat(response.products()).isEmpty();
        assertThat(response.designatedBrands()).isEmpty();
        assertThat(response.brands()).isEmpty();
        assertThat(response.version()).isEqualTo(0L);
    }

    @Test
    void save_persistsJoinedTextAndSplitsOnRead() {
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.empty());
        when(snowflakeIdGenerator.nextId()).thenReturn(1L, 2L, 3L);
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteProjectConfigResponse response = store().save(88L, new QuoteProjectConfigRequest(
                new BigDecimal("40"), true,
                List.of("螺纹钢|HRB400|12|9米", "盘螺|HRB400|8|-"),
                List.of("沙钢", "中天"), "重点客户",
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("30"),
                        List.of("螺纹钢", "盘钢"), 0))), null);

        assertThat(response.lengthPremium()).isEqualByComparingTo("40");
        assertThat(response.hrb400eFallback()).isTrue();
        assertThat(response.products()).containsExactly("螺纹钢|HRB400|12|9米", "盘螺|HRB400|8|-");
        assertThat(response.designatedBrands()).containsExactly("沙钢", "中天");
        assertThat(response.brands()).hasSize(1);
        assertThat(response.brands().get(0).categories()).containsExactly("螺纹钢", "盘钢");
        verify(repository).saveAndFlush(any(QuoteProjectConfig.class));
    }

    @Test
    void save_absentConfigWithZeroExpectedVersion_creates() {
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.empty());
        when(snowflakeIdGenerator.nextId()).thenReturn(1L, 2L, 3L);
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteProjectConfigResponse response = store().save(88L, new QuoteProjectConfigRequest(
                new BigDecimal("30"), false, List.of(), List.of(), null, List.of()), 0L);

        assertThat(response.projectId()).isEqualTo(88L);
        verify(repository).saveAndFlush(any(QuoteProjectConfig.class));
    }

    @Test
    void save_rejectsDuplicateBrand() {
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.empty());

        QuoteProjectConfigRequest duplicated = new QuoteProjectConfigRequest(
                new BigDecimal("30"), false, List.of(), List.of(), null,
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("30"), List.of(), 0),
                        new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("20"), List.of(), 1)));

        assertThatThrownBy(() -> store().save(88L, duplicated, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可重复");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void save_acceptsMatchingExpectedVersion() {
        QuoteProjectConfig existing = existingConfig(5L);
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteProjectConfigResponse response = store().save(88L, new QuoteProjectConfigRequest(
                new BigDecimal("40"), false, List.of(), List.of(), null, List.of()), 5L);

        assertThat(response.lengthPremium()).isEqualByComparingTo("40");
        verify(repository).saveAndFlush(any(QuoteProjectConfig.class));
    }

    @Test
    void save_rejectsStaleExpectedVersion() {
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.of(existingConfig(5L)));

        assertThatThrownBy(() -> store().save(88L, new QuoteProjectConfigRequest(
                new BigDecimal("40"), false, List.of(), List.of(), null, List.of()), 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本已变更");
        verify(repository, never()).saveAndFlush(any());
    }

    /**
     * 回归: 同一 projectId 连续两次保存相同品牌(模拟重新进入页面再次保存),
     * 必须复用原品牌实体、保持雪花 id 不变, 且不再分配新 id。
     * <p>旧实现 clear + 重新 add 会让每次保存都产生新子实体 id, 生产上触发
     * {@code uk_quote_project_brand(config_id, brand_name)} 冲突并映射为 409。
     */
    @Test
    void save_sameBrandTwice_reusesExistingBrandEntityAndKeepsId() {
        QuoteProjectConfig existing = new QuoteProjectConfig();
        existing.setId(1L);
        existing.setProjectId(88L);
        existing.setVersion(0L);
        existing.getBrands().add(projectBrand(existing, 999L, "中天", "30"));

        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteProjectConfigRequest request = new QuoteProjectConfigRequest(
                new BigDecimal("30"), false, List.of(), List.of(), null,
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("28"),
                        List.of("螺纹钢", "盘螺"), 0)));

        QuoteProjectConfigResponse first = store().save(88L, request, null);
        QuoteProjectConfigResponse second = store().save(88L, request, null);

        assertThat(first.brands()).hasSize(1);
        assertThat(second.brands()).hasSize(1);
        assertThat(existing.getBrands()).hasSize(1);
        assertThat(existing.getBrands().get(0).getId()).isEqualTo(999L);
        assertThat(existing.getBrands().get(0).getFreight()).isEqualByComparingTo("28");
        assertThat(second.brands().get(0).categories()).containsExactly("螺纹钢", "盘螺");
        verify(snowflakeIdGenerator, never()).nextId();
    }

    /** 回归: 按名称协调——命中名称复用旧实体, 请求新增才创建, 库中多余项被移除。 */
    @Test
    void save_reconcilesBrandsByName_reusesKeepsRemovesAndAdds() {
        QuoteProjectConfig existing = new QuoteProjectConfig();
        existing.setId(1L);
        existing.setProjectId(88L);
        existing.setVersion(0L);
        existing.getBrands().add(projectBrand(existing, 111L, "中天", "30"));
        existing.getBrands().add(projectBrand(existing, 222L, "沙钢", "40"));

        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));
        when(snowflakeIdGenerator.nextId()).thenReturn(333L);

        store().save(88L, new QuoteProjectConfigRequest(
                new BigDecimal("30"), false, List.of(), List.of(), null,
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("30"), List.of(), 0),
                        new QuoteProjectConfigRequest.BrandRequest("亚新", new BigDecimal("35"), List.of(), 1))),
                null);

        assertThat(existing.getBrands()).extracting(QuoteProjectBrand::getBrandName)
                .containsExactly("中天", "亚新");
        assertThat(existing.getBrands().get(0).getId()).isEqualTo(111L);
        assertThat(existing.getBrands().get(1).getId()).isEqualTo(333L);
    }

    /**
     * 回归(并发保护): 整体替换语义下, 即使仅改动品牌的子集合, 也必须显式对已有配置加
     * {@code OPTIMISTIC_FORCE_INCREMENT}, 保证父配置 {@code @Version} 每次替换恰好 +1。
     */
    @Test
    void save_existingConfig_forcesVersionIncrementEvenForBrandOnlyChange() {
        QuoteProjectConfig existing = existingConfig(5L);
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));
        doAnswer(invocation -> {
            QuoteProjectConfig config = invocation.getArgument(0);
            config.setVersion(config.getVersion() + 1);
            return null;
        }).when(entityManager).lock(any(QuoteProjectConfig.class), eq(LockModeType.OPTIMISTIC_FORCE_INCREMENT));

        QuoteProjectConfigResponse response = store().save(88L, new QuoteProjectConfigRequest(
                new BigDecimal("30"), false, List.of(), List.of(), null,
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("30"), List.of(), 0))), 5L);

        assertThat(response.version()).isEqualTo(6L);
        verify(entityManager).lock(existing, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    /**
     * P1-2 回归: 整体替换同时变更标量字段时, 父行被自然标脏并由 {@code @Version} 递增一次,
     * 不得再叠加 FORCE_INCREMENT(否则 +2)。
     */
    @Test
    void save_existingConfig_withScalarChange_doesNotForceIncrement() {
        QuoteProjectConfig existing = existingConfig(5L);
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        store().save(88L, new QuoteProjectConfigRequest(
                new BigDecimal("40"), false, List.of(), List.of(), null,
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("30"), List.of(), 0))), 5L);

        verify(entityManager, never()).lock(any(QuoteProjectConfig.class),
                eq(LockModeType.OPTIMISTIC_FORCE_INCREMENT));
    }

    private QuoteProjectBrand projectBrand(QuoteProjectConfig config, Long id, String name, String freight) {
        QuoteProjectBrand brand = new QuoteProjectBrand();
        brand.setId(id);
        brand.setConfig(config);
        brand.setBrandName(name);
        brand.setFreight(new BigDecimal(freight));
        brand.setSortOrder(0);
        return brand;
    }

    private QuoteProjectConfig existingConfig(Long version) {
        QuoteProjectConfig config = new QuoteProjectConfig();
        config.setId(1L);
        config.setProjectId(88L);
        config.setVersion(version);
        // 标量与"仅改品牌"请求一致, 用于验证纯子集合变更时显式 FORCE_INCREMENT。
        config.setLengthPremium(new BigDecimal("30"));
        config.setHrb400eFallback(false);
        return config;
    }
}
