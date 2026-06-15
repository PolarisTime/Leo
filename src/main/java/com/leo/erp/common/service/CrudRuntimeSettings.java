package com.leo.erp.common.service;

import java.util.Set;

public interface CrudRuntimeSettings {
    boolean shouldUseSnowflakeIdAsBusinessNo();

    boolean shouldAdminSeeDeletedRecords();

    Set<String> getHiddenAuditedStatuses();
}
