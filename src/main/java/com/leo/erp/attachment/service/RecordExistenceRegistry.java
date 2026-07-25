package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ModuleCatalog;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RecordExistenceRegistry {

    private final Map<String, RecordExistencePort> portsByModuleKey;
    private final ModuleCatalog moduleCatalog;

    public RecordExistenceRegistry(List<RecordExistencePort> ports, ModuleCatalog moduleCatalog) {
        this.moduleCatalog = moduleCatalog;
        if (ports.isEmpty()) {
            throw new IllegalStateException("At least one RecordExistencePort must be registered");
        }
        Map<String, RecordExistencePort> registrations = new LinkedHashMap<>();
        for (RecordExistencePort port : ports) {
            String moduleKey = normalizeModuleKey(port.moduleKey());
            if (moduleKey.isBlank()) {
                throw new IllegalStateException("RecordExistencePort moduleKey must not be blank");
            }
            RecordExistencePort existing = registrations.putIfAbsent(moduleKey, port);
            if (existing != null) {
                throw new IllegalStateException("Duplicate RecordExistencePort registration: " + moduleKey);
            }
        }
        this.portsByModuleKey = Map.copyOf(registrations);
    }

    public RecordExistencePort require(String moduleKey) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        RecordExistencePort port = portsByModuleKey.get(normalizedModuleKey);
        if (port == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前模块不支持附件");
        }
        return port;
    }

    public String normalizeModuleKey(String moduleKey) {
        String normalized = moduleCatalog.normalizeModuleKey(moduleKey);
        return normalized == null ? "" : normalized;
    }
}
