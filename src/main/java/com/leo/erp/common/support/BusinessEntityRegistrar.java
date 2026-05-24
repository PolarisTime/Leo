package com.leo.erp.common.support;

import com.leo.erp.common.persistence.AbstractAuditableEntity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BusinessEntityRegistrar {

    private final Map<String, Class<? extends AbstractAuditableEntity>> store = new LinkedHashMap<>();

    public void register(String moduleKey, Class<? extends AbstractAuditableEntity> entityType) {
        store.put(normalizeModuleKey(moduleKey), entityType);
    }

    public Optional<Class<? extends AbstractAuditableEntity>> findEntityType(String moduleKey) {
        return Optional.ofNullable(store.get(normalizeModuleKey(moduleKey)));
    }

    public boolean hasEntity(String moduleKey) {
        return findEntityType(moduleKey).isPresent();
    }

    public Set<String> moduleKeys() {
        return store.keySet();
    }

    public static String normalizeModuleKey(String moduleKey) {
        return String.valueOf(moduleKey == null ? "" : moduleKey)
                .trim()
                .replaceFirst("^/+", "")
                .toLowerCase(Locale.ROOT);
    }

    public Map<String, Class<? extends AbstractAuditableEntity>> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(store));
    }
}
