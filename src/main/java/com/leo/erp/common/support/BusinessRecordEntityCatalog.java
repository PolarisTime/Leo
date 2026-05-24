package com.leo.erp.common.support;

import com.leo.erp.common.persistence.AbstractAuditableEntity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @deprecated 请直接注入 {@link BusinessEntityRegistrar} 替代静态调用。
 * 实体注册已迁移至 {@code com.leo.erp.config.BusinessEntityConfig}，
 * 消除了 common 包对业务模块的编译期反向依赖。
 */
@Deprecated
public final class BusinessRecordEntityCatalog {

    private static volatile BusinessEntityRegistrar registrar;

    private BusinessRecordEntityCatalog() {
    }

    /** 由 BusinessEntityConfig 在 Spring 启动时调用 */
    public static void setRegistrar(BusinessEntityRegistrar r) {
        registrar = r;
    }

    private static BusinessEntityRegistrar reg() {
        BusinessEntityRegistrar r = registrar;
        if (r == null) {
            throw new IllegalStateException("BusinessEntityRegistrar not initialized");
        }
        return r;
    }

    public static Optional<Class<? extends AbstractAuditableEntity>> findEntityType(String moduleKey) {
        return reg().findEntityType(moduleKey);
    }

    public static boolean hasEntity(String moduleKey) {
        return reg().hasEntity(moduleKey);
    }

    public static Set<String> moduleKeys() {
        return reg().moduleKeys();
    }

    public static String normalizeModuleKey(String moduleKey) {
        return BusinessEntityRegistrar.normalizeModuleKey(moduleKey);
    }
}
