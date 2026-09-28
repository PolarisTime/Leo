package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 值映射/别名的<b>唯一归一化入口</b>(类别 / 材质 / 定尺 / 品牌四个维度)。
 *
 * <p>规则来源已从硬编码改为可维护的 {@code md_value_alias} 表(V171); 归一化口径:</p>
 *
 * <ol>
 *   <li><b>维度内精确查表</b>: 用该维度的查表键(类别/材质/品牌 = trim 后的原写法; 定尺 = 定尺口径归一后的写法)
 *       在映射表里命中 → 直接取目标写法;</li>
 *   <li><b>现有归一化回退</b>: 未命中映射行时, 类别回退到 {@link CategoryNormalizer}
 *       (硬编码常量 {@code 直条} → {@code 螺纹钢}, 仅作为"表数据缺失/未执行 V171"时的兜底, 行为不变),
 *       定尺回退到 {@link MaterialSpecCatalogQuery#normalizeLength(String)}(结构归一:
 *       {@code -}/空 → 空串, {@code 9m}/{@code 9 M} → {@code 9米});</li>
 *   <li>材质/品牌未命中时原样返回(仅 trim), 不做猜测。</li>
 * </ol>
 *
 * <p><b>语义边界</b>: 只做同一含义多写法归一, 不做跨语义合并; 自映射由写入口拒绝。</p>
 *
 * <p>批量取映射走 {@link ValueAliasMappings} 的命名缓存(按维度一次查询, 请求内复用),
 * 不再逐值查库。</p>
 */
@Service
public class ValueAliasQuery {

    private final ValueAliasMappings mappingsLoader;

    public ValueAliasQuery(ValueAliasMappings mappingsLoader) {
        this.mappingsLoader = mappingsLoader;
    }

    /** 某维度的全部映射(缓存命中则不发 SQL)。 */
    public Map<String, String> mappings(ValueAliasDimension dimension) {
        return mappingsLoader.load(dimension);
    }

    /**
     * 一次性取四个维度的映射, 供同一请求内多处复用(字典投影/保存校验/推导匹配/品牌匹配),
     * 避免逐值查库或逐维度重复取。
     */
    public AliasRules rules() {
        return new AliasRules(
                mappingsLoader.load(ValueAliasDimension.CATEGORY),
                mappingsLoader.load(ValueAliasDimension.MATERIAL),
                mappingsLoader.load(ValueAliasDimension.LENGTH),
                mappingsLoader.load(ValueAliasDimension.BRAND));
    }

    /**
     * 单值归一: 映射命中 → 目标写法; 未命中 → 现有归一化回退。
     *
     * @param dimension 维度
     * @param value     原写法, 允许 null(按各维度既有空值语义返回)
     */
    public String normalize(ValueAliasDimension dimension, String value) {
        return resolveValue(dimension, value, mappingsLoader.load(dimension));
    }

    /**
     * 预览"这个写法会归一到什么"(只读, 供前端做映射预览): 同时返回是否命中映射行。
     *
     * @param dimension 维度
     * @param value     原写法(调用方需保证非空白)
     */
    public Resolution resolve(ValueAliasDimension dimension, String value) {
        Map<String, String> mappings = mappingsLoader.load(dimension);
        String key = lookupKey(dimension, value);
        boolean matched = key != null && mappings.containsKey(key);
        return new Resolution(dimension, value == null ? null : value.trim(), key,
                resolveValue(dimension, value, mappings), matched);
    }

    /**
     * 文本维度的预览重载: 非法维度或空值统一按 422(VALIDATION_ERROR)抛出, 与写入口径一致。
     *
     * @throws com.leo.erp.common.error.BusinessException 维度非法或 value 为空白
     */
    public Resolution resolveByText(String dimension, String value) {
        ValueAliasDimension parsed;
        try {
            parsed = ValueAliasDimension.parse(dimension);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "value 不能为空");
        }
        return resolve(parsed, value);
    }

    /**
     * 归一化的核心实现(单点): 查表键 → 映射命中取目标值 → 未命中走现有归一化回退。
     *
     * <p>类别额外对映射目标值再走一次 {@link CategoryNormalizer}(保证 {@code 某别名 → 直条} 与
     * {@code 直条 → 螺纹钢} 的传递归一结果一致)。</p>
     */
    static String resolveValue(ValueAliasDimension dimension, String raw, Map<String, String> mappings) {
        if (dimension == ValueAliasDimension.LENGTH) {
            String canonical = MaterialSpecCatalogQuery.normalizeLength(raw);
            String mapped = mappings.get(canonical);
            return canonicalize(dimension, mapped == null ? canonical : mapped);
        }
        String trimmed = raw == null ? null : raw.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        String mapped = mappings.get(trimmed);
        return canonicalize(dimension, mapped == null ? trimmed : mapped);
    }

    /**
     * 该维度的查表键:
     * <ul>
     *   <li>类别/材质/品牌 = trim 后的原写法(保留用户书写, 才能命中"原写法 → 规范写法"的映射行);</li>
     *   <li>定尺 = 先按定尺口径结构归一(写入时同口径), 保证 {@code 9m} 与 {@code 9米} 不会各存一行。</li>
     * </ul>
     */
    static String lookupKey(ValueAliasDimension dimension, String raw) {
        if (dimension == ValueAliasDimension.LENGTH) {
            return MaterialSpecCatalogQuery.normalizeLength(raw);
        }
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 未命中映射时的现有归一化回退(类别: 硬编码兜底; 其余原样)。 */
    static String canonicalize(ValueAliasDimension dimension, String value) {
        return dimension == ValueAliasDimension.CATEGORY ? CategoryNormalizer.normalize(value) : value;
    }

    /**
     * 预览结果。
     *
     * @param value       归一前的原写法(trim 后)
     * @param lookupKey   实际用于查表的键(定尺为结构归一后的写法)
     * @param targetValue 归一后的写法
     * @param matched     是否命中映射行(false 表示走的是现有归一化回退/原样返回)
     */
    public record Resolution(ValueAliasDimension dimension, String value, String lookupKey,
                             String targetValue, boolean matched) {
    }

    /**
     * 四个维度的映射快照(同一请求内复用)。
     *
     * <p>空快照 {@link #EMPTY} 的行为 == 没有配置任何映射: 类别仍按 {@link CategoryNormalizer} 兜底,
     * 其余维度原样(即"未配置映射时行为与改造前一致")。</p>
     */
    public record AliasRules(Map<String, String> category,
                             Map<String, String> material,
                             Map<String, String> length,
                             Map<String, String> brand) {

        public static final AliasRules EMPTY = new AliasRules(Map.of(), Map.of(), Map.of(), Map.of());

        /** 类别归一(映射命中优先, 未命中回退 {@link CategoryNormalizer})。 */
        public String normalizeCategory(String raw) {
            return resolveValue(ValueAliasDimension.CATEGORY, raw, category);
        }

        /** 材质归一(映射命中优先, 未命中 trim 后原样)。 */
        public String normalizeMaterial(String raw) {
            return resolveValue(ValueAliasDimension.MATERIAL, raw, material);
        }

        /** 定尺归一(定尺口径结构归一 → 映射命中优先)。 */
        public String normalizeLength(String raw) {
            return resolveValue(ValueAliasDimension.LENGTH, raw, length);
        }

        /** 品牌归一(映射命中优先, 未命中 trim 后原样)。 */
        public String normalizeBrand(String raw) {
            return resolveValue(ValueAliasDimension.BRAND, raw, brand);
        }

        /** 两个值在该维度归一后是否表示同一含义。 */
        public boolean sameValue(ValueAliasDimension dimension, String left, String right) {
            String normalizedLeft = normalize(dimension, left);
            String normalizedRight = normalize(dimension, right);
            return normalizedLeft == null ? normalizedRight == null : normalizedLeft.equals(normalizedRight);
        }

        /** 两个类别名在归一后是否表示同一类别(替代直接调用 {@link CategoryNormalizer#sameCategory})。 */
        public boolean sameCategory(String left, String right) {
            return sameValue(ValueAliasDimension.CATEGORY, left, right);
        }

        /** 任意维度的归一(带维度参数的重载)。 */
        public String normalize(ValueAliasDimension dimension, String raw) {
            return switch (dimension) {
                case CATEGORY -> normalizeCategory(raw);
                case MATERIAL -> normalizeMaterial(raw);
                case LENGTH -> normalizeLength(raw);
                case BRAND -> normalizeBrand(raw);
            };
        }

        /** 价格表条目键(与价格表条目/比价行键同口径): 四段归一后拼接。 */
        public String itemKey(String category, String material, Integer spec, String length) {
            return MaterialSpecCatalogQuery.key(normalizeCategory(category), normalizeMaterial(material),
                    spec, normalizeLength(length));
        }
    }
}
