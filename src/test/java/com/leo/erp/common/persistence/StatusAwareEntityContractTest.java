package com.leo.erp.common.persistence;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体状态契约：<b>凡声明 {@code status} 字段的 JPA 实体都必须实现 {@link StatusAwareEntity}</b>。
 *
 * <p>为什么要有这条断言：{@code CrudStatusGuard} 只能接管 {@link StatusAwareEntity}，未实现该接口的
 * 实体只能由服务层绕过守卫直写 {@code status}，状态迁移校验对其永远不可用（历史上 28 个有 status 的
 * 实体中有 13 个落入这一状态）。新增实体只要带 status 字段就必须同时实现契约，否则此用例失败。</p>
 */
class StatusAwareEntityContractTest {

    private static final String BASE_PACKAGE = "com.leo.erp";

    /** 已知带 status 的关键实体：既防扫描空跑假绿，也钉住主数据类不得回退。 */
    private static final Set<String> KNOWN_STATUS_ENTITIES = Set.of(
            "Customer", "Supplier", "Carrier", "Warehouse", "Project", "MaterialCategory",
            "SysRole", "CompanySetting", "PrintTemplate", "SecuritySecret", "QuoteSheet",
            "SupplierPriceList", "UserAccount", "SalesOrder", "PurchaseInbound", "Material"
    );

    @Test
    void everyEntityWithStatusFieldShouldImplementStatusAwareEntity() throws ClassNotFoundException {
        List<Class<?>> scannedEntities = scanEntities();
        assertThat(scannedEntities)
                .as("实体扫描不得空跑：至少应扫描到 60 个 @Entity 类")
                .hasSizeGreaterThanOrEqualTo(60);

        Set<String> statusEntityNames = new LinkedHashSet<>();
        List<Class<?>> violations = new ArrayList<>();
        for (Class<?> entityType : scannedEntities) {
            if (!hasStatusField(entityType)) {
                continue;
            }
            statusEntityNames.add(entityType.getSimpleName());
            if (!StatusAwareEntity.class.isAssignableFrom(entityType)) {
                violations.add(entityType);
            }
        }

        assertThat(statusEntityNames)
                .as("带 status 字段的实体集合必须覆盖已知主数据/单据实体，防止扫描口径漂移")
                .containsAll(KNOWN_STATUS_ENTITIES);
        assertThat(violations)
                .as("以下实体有 status 字段但未实现 StatusAwareEntity，状态写守卫无法接管")
                .isEmpty();
    }

    private static List<Class<?>> scanEntities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> entities = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            entities.add(Class.forName(candidate.getBeanClassName()));
        }
        return entities;
    }

    private static boolean hasStatusField(Class<?> entityType) {
        return Arrays.stream(entityType.getDeclaredFields())
                .anyMatch(field -> "status".equals(field.getName()) && !field.isSynthetic());
    }
}
