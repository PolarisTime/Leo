package com.leo.erp.master.project.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目资料「品牌 → 启用品种」的 JSONB 读写（{@code md_project.quote_brand_categories}）。
 *
 * <p>库里是 JSONB、实体上是原始 JSON 文本（{@code @ColumnTransformer} 负责 {@code ::jsonb} 转换），
 * 因此「文本 ↔ Map」的解析只在这里做一次：MapStruct 映射、跨模块快照、保存路径都走同一对方法，
 * 避免三处各写一遍解析而漂移。</p>
 *
 * <p><b>空值语义</b>：{@code null}/空 Map 落库为 {@code NULL}；读取时空值或结构非法一律返回空 Map
 * （缺键 = 该品牌全部品种，所以空 Map 与「不限制」等价，不会让历史数据行为突变）。</p>
 */
@Slf4j
public final class QuoteBrandCategories {

    /** 单个品牌/品种 key 的清洗上限，与 md_project 其它多值文本一致。 */
    private static final int MAX_TOKEN_LENGTH = 64;

    private static final TypeReference<Map<String, List<String>>> TYPE =
            new TypeReference<>() {
            };

    /** 解析用 ObjectMapper：只处理 {@code Map<String, List<String>>}，与全局序列化策略无关。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QuoteBrandCategories() {
    }

    /**
     * Map → JSONB 文本（去空白、去空项；空 Map 归一为 {@code null}）。
     *
     * @param categories 品牌 → 品种列表；允许 null
     * @return JSON 文本，或 null 表示不限制
     */
    public static String write(Map<String, List<String>> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        Map<String, List<String>> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
            String brand = cleanToken(entry.getKey());
            List<String> values = cleanValues(entry.getValue());
            if (brand == null || values.isEmpty()) {
                continue;
            }
            cleaned.put(brand, values);
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(cleaned);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化项目品牌品种配置失败", ex);
        }
    }

    /**
     * JSONB 文本 → Map（保持 JSON 里的键顺序；非法结构返回空 Map 并告警，不让一条脏数据把项目详情打成 500）。
     *
     * @param raw 数据库原始文本，允许 null
     * @return 不可变 Map，永不为 null
     */
    public static Map<String, List<String>> read(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, List<String>> parsed = MAPPER.readValue(raw, TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return Map.of();
            }
            Map<String, List<String>> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
                String brand = cleanToken(entry.getKey());
                List<String> values = cleanValues(entry.getValue());
                if (brand == null || values.isEmpty()) {
                    continue;
                }
                cleaned.put(brand, values);
            }
            return cleaned.isEmpty() ? Map.of() : Map.copyOf(cleaned);
        } catch (JsonProcessingException ex) {
            log.warn("解析项目品牌品种配置失败，按空配置处理: {}", raw, ex);
            return Map.of();
        }
    }

    private static List<String> cleanValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            String token = cleanToken(value);
            if (token != null && !cleaned.contains(token)) {
                cleaned.add(token);
            }
        }
        return List.copyOf(cleaned);
    }

    private static String cleanToken(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TOKEN_LENGTH) {
            return null;
        }
        return trimmed;
    }
}
