package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.market.pricelist.domain.entity.ValueAlias;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import com.leo.erp.market.pricelist.repository.ValueAliasRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 值映射归一化口径测试(单点实现).
 *
 * <p>覆盖: 映射命中优先、未命中回退现有归一化({@code 直条} → {@code 螺纹钢} / 定尺结构归一)、
 * 四个维度各自的查表键、预览匹配标记、文本维度校验(非法维度/空值 → 422)与空映射行为等价性。</p>
 */
class ValueAliasQueryTest {

    /** 空映射(等价于"未配置任何映射"): 归一化必须与改造前完全一致。 */
    private static ValueAliasQuery query() {
        return query(Map.of());
    }

    /** 按维度配置映射的查询服务(仓储按维度返回对应行)。 */
    private static ValueAliasQuery query(Map<ValueAliasDimension, List<ValueAlias>> mappings) {
        ValueAliasRepository repository = mock(ValueAliasRepository.class);
        when(repository.findByDimensionAndDeletedFlagFalseOrderBySourceValueAscIdAsc(any()))
                .thenAnswer(invocation -> new ArrayList<>(
                        mappings.getOrDefault(invocation.getArgument(0), List.of())));
        return new ValueAliasQuery(new ValueAliasMappings(repository));
    }

    private static ValueAlias alias(Long id, ValueAliasDimension dimension, String source, String target) {
        ValueAlias alias = new ValueAlias();
        alias.setId(id);
        alias.setDimension(dimension);
        alias.setSourceValue(source);
        alias.setTargetValue(target);
        return alias;
    }

    /** 命中 CATEGORY 映射 → 目标写法(映射优先于硬编码兜底)。 */
    @Test
    void normalizeCategory_prefersMappingRow() {
        ValueAliasQuery query = query(Map.of(ValueAliasDimension.CATEGORY,
                List.of(alias(1L, ValueAliasDimension.CATEGORY, "三级钢", "盘螺"))));

        assertThat(query.normalize(ValueAliasDimension.CATEGORY, "三级钢")).isEqualTo("盘螺");
        // 未配置的写法仍走硬编码兜底
        assertThat(query.normalize(ValueAliasDimension.CATEGORY, "直条")).isEqualTo("螺纹钢");
    }

    /** 未配置映射时类别仍按 CategoryNormalizer 兜底(直条 ≡ 螺纹钢), 行为与改造前一致。 */
    @Test
    void normalizeCategory_fallsBackToLegacyConstantWhenNoMapping() {
        ValueAliasQuery query = query();

        assertThat(query.normalize(ValueAliasDimension.CATEGORY, "直条")).isEqualTo("螺纹钢");
        assertThat(query.normalize(ValueAliasDimension.CATEGORY, " 直条 ")).isEqualTo("螺纹钢");
        assertThat(query.normalize(ValueAliasDimension.CATEGORY, "螺纹钢")).isEqualTo("螺纹钢");
        assertThat(query.normalize(ValueAliasDimension.CATEGORY, "盘螺")).isEqualTo("盘螺");
        assertThat(query.normalize(ValueAliasDimension.CATEGORY, null)).isNull();
        assertThat(query.normalize(ValueAliasDimension.CATEGORY, "  ")).isNull();
    }

    /** 映射目标再走一次类别兜底: 别名 → 直条 与 直条 → 螺纹钢 的传递归一结果一致。 */
    @Test
    void normalizeCategory_canonicalizesMappingTarget() {
        ValueAliasQuery query = query(Map.of(ValueAliasDimension.CATEGORY,
                List.of(alias(2L, ValueAliasDimension.CATEGORY, "三级螺纹", "直条"))));

        assertThat(query.normalize(ValueAliasDimension.CATEGORY, "三级螺纹")).isEqualTo("螺纹钢");
    }

    /** 材质: 命中取目标值, 未命中 trim 后原样, null 保持 null。 */
    @Test
    void normalizeMaterial() {
        ValueAliasQuery query = query(Map.of(ValueAliasDimension.MATERIAL,
                List.of(alias(3L, ValueAliasDimension.MATERIAL, "抗震钢", "抗震钢E"))));

        assertThat(query.normalize(ValueAliasDimension.MATERIAL, "抗震钢")).isEqualTo("抗震钢E");
        assertThat(query.normalize(ValueAliasDimension.MATERIAL, " HRB400 ")).isEqualTo("HRB400");
        assertThat(query.normalize(ValueAliasDimension.MATERIAL, null)).isNull();
    }

    /**
     * 定尺: 先按定尺口径结构归一(- → 空串, 9m → 9米, 超长截断), 再叠加 LENGTH 映射;
     * 因此映射键用规范写法, {@code 12} 与 {@code 12米} 可以各存一行而不会重复。
     */
    @Test
    void normalizeLength_appliesStructuralNormalizationThenMapping() {
        ValueAliasQuery query = query(Map.of(ValueAliasDimension.LENGTH,
                List.of(alias(4L, ValueAliasDimension.LENGTH, "12", "12米"))));

        assertThat(query.normalize(ValueAliasDimension.LENGTH, "12")).isEqualTo("12米");
        // 结构归一侧保持既有行为
        assertThat(query.normalize(ValueAliasDimension.LENGTH, "9m")).isEqualTo("9米");
        assertThat(query.normalize(ValueAliasDimension.LENGTH, "-")).isEmpty();
        assertThat(query.normalize(ValueAliasDimension.LENGTH, null)).isEmpty();
        assertThat(query.normalize(ValueAliasDimension.LENGTH, "1234567890ABCDEFGHIJ"))
                .isEqualTo("1234567890ABCDEF");
    }

    /** 品牌: 命中取目标值, 未命中 trim 后原样。 */
    @Test
    void normalizeBrand() {
        ValueAliasQuery query = query(Map.of(ValueAliasDimension.BRAND,
                List.of(alias(5L, ValueAliasDimension.BRAND, "富鑫", "安徽富鑫"))));

        assertThat(query.normalize(ValueAliasDimension.BRAND, " 富鑫 ")).isEqualTo("安徽富鑫");
        assertThat(query.normalize(ValueAliasDimension.BRAND, "武钢汉钢")).isEqualTo("武钢汉钢");
        assertThat(query.normalize(ValueAliasDimension.BRAND, null)).isNull();
    }

    /** 预览: 命中映射时 matched=true 且回显查表键; 未命中时 matched=false(走现有归一化回退)。 */
    @Test
    void resolve_reportsMatchedFlagAndLookupKey() {
        ValueAliasQuery query = query(Map.of(
                ValueAliasDimension.CATEGORY,
                List.of(alias(6L, ValueAliasDimension.CATEGORY, "直条", "螺纹钢")),
                ValueAliasDimension.LENGTH,
                List.of(alias(7L, ValueAliasDimension.LENGTH, "9米", "9"))));

        ValueAliasQuery.Resolution hit = query.resolve(ValueAliasDimension.CATEGORY, "直条");
        assertThat(hit.matched()).isTrue();
        assertThat(hit.lookupKey()).isEqualTo("直条");
        assertThat(hit.targetValue()).isEqualTo("螺纹钢");
        assertThat(hit.value()).isEqualTo("直条");

        // 未配置映射的写法: matched=false, 但目标值仍是归一化回退结果
        ValueAliasQuery.Resolution fallback = query.resolve(ValueAliasDimension.CATEGORY, "盘螺");
        assertThat(fallback.matched()).isFalse();
        assertThat(fallback.targetValue()).isEqualTo("盘螺");

        // 定尺查表键 = 结构归一后的写法
        ValueAliasQuery.Resolution length = query.resolve(ValueAliasDimension.LENGTH, "9m");
        assertThat(length.lookupKey()).isEqualTo("9米");
        assertThat(length.matched()).isTrue();
        assertThat(length.targetValue()).isEqualTo("9");
    }

    /** 文本维度重载: 非法维度/空值 → 422(VALIDATION_ERROR), 与写入口径一致。 */
    @Test
    void resolveByText_rejectsInvalidDimensionAndBlankValue() {
        ValueAliasQuery query = query();

        assertThatThrownBy(() -> query.resolveByText("COLOR", "红"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维度只能是");
        assertThatThrownBy(() -> query.resolveByText(null, "红"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维度不能为空");
        assertThatThrownBy(() -> query.resolveByText("CATEGORY", "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("value 不能为空");
        assertThat(query.resolveByText("category", "直条").targetValue()).isEqualTo("螺纹钢");
    }

    /** AliasRules: 一次取四维度, sameCategory/sameValue/itemKey 与单值归一一致。 */
    @Test
    void aliasRules_shareTheSameNormalization() {
        ValueAliasQuery query = query(Map.of(
                ValueAliasDimension.CATEGORY,
                List.of(alias(8L, ValueAliasDimension.CATEGORY, "三级钢", "直条")),
                ValueAliasDimension.MATERIAL,
                List.of(alias(9L, ValueAliasDimension.MATERIAL, "抗震钢", "抗震钢E"))));
        ValueAliasQuery.AliasRules rules = query.rules();

        assertThat(rules.normalizeCategory("三级钢")).isEqualTo("螺纹钢");
        assertThat(rules.normalizeMaterial("抗震钢")).isEqualTo("抗震钢E");
        assertThat(rules.sameCategory("直条", "螺纹钢")).isTrue();
        assertThat(rules.sameCategory("直条", "盘螺")).isFalse();
        assertThat(rules.sameValue(ValueAliasDimension.MATERIAL, "抗震钢", "抗震钢E")).isTrue();
        assertThat(rules.itemKey("三级钢", "抗震钢", 12, "9m"))
                .isEqualTo("螺纹钢|抗震钢E|12|9米");

        // 空快照: 类别走硬编码兜底, 其余原样
        assertThat(ValueAliasQuery.AliasRules.EMPTY.normalizeCategory("直条")).isEqualTo("螺纹钢");
        assertThat(ValueAliasQuery.AliasRules.EMPTY.normalizeMaterial(" HRB400 ")).isEqualTo("HRB400");
        assertThat(ValueAliasQuery.AliasRules.EMPTY.normalizeLength("9M")).isEqualTo("9米");
        assertThat(ValueAliasQuery.AliasRules.EMPTY.normalizeBrand(" 武钢 ")).isEqualTo("武钢");
    }

    /** 同维度同源值历史脏数据(理论上被唯一索引挡住)下结果必须确定: 取源值升序第一条。 */
    @Test
    void mappings_dedupeBySourceValueDeterministically() {
        ValueAliasRepository repository = mock(ValueAliasRepository.class);
        when(repository.findByDimensionAndDeletedFlagFalseOrderBySourceValueAscIdAsc(any()))
                .thenReturn(new ArrayList<>(List.of(
                        alias(11L, ValueAliasDimension.BRAND, "富鑫", "安徽富鑫"),
                        alias(12L, ValueAliasDimension.BRAND, "富鑫", "富鑫钢铁"))));
        ValueAliasQuery query = new ValueAliasQuery(new ValueAliasMappings(repository));

        assertThat(query.normalize(ValueAliasDimension.BRAND, "富鑫")).isEqualTo("安徽富鑫");
    }
}
