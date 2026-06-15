package com.leo.erp.common.service;

public record CreateEntityId(long id, String preallocatedModuleKey) {

    public boolean hasPreallocatedModuleKey() {
        return preallocatedModuleKey != null && !preallocatedModuleKey.isBlank();
    }
}
