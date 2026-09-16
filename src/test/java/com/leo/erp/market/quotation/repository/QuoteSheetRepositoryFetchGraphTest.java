package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import jakarta.persistence.OrderColumn;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 轻量回归防护: 直接断言 {@link QuoteSheetRepository#findByIdAndDeletedFlagFalse(Long)} 的
 * {@code @EntityGraph.attributePaths} 不会同时 fetch join 两个 bag。
 * <p>
 * Hibernate 6.6 下"一次查询 fetch join 多个 bag"(包括"父 bag + 嵌套 bag"组合, 例如
 * {@code items} 与 {@code items.prices})会在查询构建阶段抛 {@code MultipleBagFetchException},
 * 被翻译成 {@code JpaSystemException} → 生产 500; 现有 Mockito 单测无法发现该运行时约束。
 * 本测试不依赖 H2/Testcontainers, 用反射在注解层面尽早拦截: bag 的定义是集合类型且未标注
 * {@link OrderColumn}(即 {@code List}/{@code Collection} 无顺序列), {@code Set} 与
 * 带 {@code @OrderColumn} 的 {@code List} 不算 bag。
 */
class QuoteSheetRepositoryFetchGraphTest {

    @Test
    void findByIdAndDeletedFlagFalse_doesNotFetchJoinMultipleBags() throws NoSuchMethodException {
        EntityGraph entityGraph = findByIdMethod().getAnnotation(EntityGraph.class);
        assertThat(entityGraph).as("@EntityGraph 缺失, 无法确认抓取策略").isNotNull();

        List<String> declaredPaths = Arrays.asList(entityGraph.attributePaths());
        List<String> bagPaths = declaredPaths.stream()
                .filter(QuoteSheetRepositoryFetchGraphTest::isBagPath)
                .toList();

        assertThat(bagPaths)
                .as("同时 fetch join 多个 bag 会触发 MultipleBagFetchException; attributePaths=%s", declaredPaths)
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void findByIdAndDeletedFlagFalse_doesNotFetchJoinBrandsWithItemPrices() throws NoSuchMethodException {
        List<String> declaredPaths = Arrays.asList(findByIdMethod().getAnnotation(EntityGraph.class).attributePaths());

        boolean conflictingPairDeclared = declaredPaths.contains("brands")
                && declaredPaths.contains("items.prices")
                && isBagPath("brands")
                && isBagPath("items.prices");

        // 生产实际异常: cannot simultaneously fetch multiple bags [QuoteSheet.brands, QuoteSheetItem.prices]
        assertThat(conflictingPairDeclared)
                .as("brands 与 items.prices 同为 bag, 不能同时出现在 @EntityGraph.attributePaths")
                .isFalse();
    }

    private static Method findByIdMethod() throws NoSuchMethodException {
        return QuoteSheetRepository.class.getMethod("findByIdAndDeletedFlagFalse", Long.class);
    }

    private static boolean isBagPath(String attributePath) {
        Class<?> currentType = QuoteSheet.class;
        String[] segments = attributePath.split("\\.");
        for (int index = 0; index < segments.length; index++) {
            Field field = fieldOf(currentType, segments[index]);
            if (field == null) {
                return false;
            }
            if (index == segments.length - 1) {
                return Collection.class.isAssignableFrom(field.getType())
                        && field.getAnnotation(OrderColumn.class) == null;
            }
            currentType = elementTypeOf(field);
        }
        return false;
    }

    private static Field fieldOf(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Class<?> elementTypeOf(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length == 1
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> elementType) {
            return elementType;
        }
        return field.getType();
    }
}
