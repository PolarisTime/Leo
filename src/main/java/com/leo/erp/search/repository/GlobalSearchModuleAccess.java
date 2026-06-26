package com.leo.erp.search.repository;

import java.util.Set;

public record GlobalSearchModuleAccess(
        String moduleKey,
        Set<Long> ownerUserIds
) {
    public static GlobalSearchModuleAccess all(String moduleKey) {
        return new GlobalSearchModuleAccess(moduleKey, null);
    }

    public boolean allDataScope() {
        return ownerUserIds == null;
    }
}
