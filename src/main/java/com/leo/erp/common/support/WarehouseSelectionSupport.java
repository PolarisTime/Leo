package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.warehouse.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WarehouseSelectionSupport {

    private static final String WAREHOUSE_CACHE_KEY = "leo:warehouse:all";
    private static final Duration WAREHOUSE_CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<String>> WAREHOUSE_LIST_TYPE = new TypeReference<>() { };

    private final WarehouseRepository warehouseRepository;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public WarehouseSelectionSupport(WarehouseRepository warehouseRepository, RedisJsonCacheSupport redisJsonCacheSupport) {
        this.warehouseRepository = warehouseRepository;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public WarehouseSelectionSupport(WarehouseRepository warehouseRepository) {
        this(warehouseRepository, null);
    }

    public String normalizeWarehouseName(String warehouseName, int lineNo, boolean required) {
        String normalized = warehouseName == null ? null : warehouseName.trim();
        if (normalized != null && normalized.isBlank()) {
            normalized = null;
        }
        if (required && normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行码头不能为空");
        }
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行码头长度不能超过128");
        }
        validateWarehouseNames(Set.of(normalized));
        return normalized;
    }

    public void validateWarehouseNames(Collection<String> warehouseNames) {
        Set<String> normalizedNames = warehouseNames == null ? Set.of() : warehouseNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        if (normalizedNames.isEmpty()) {
            return;
        }
        Set<String> configuredNames = loadActiveWarehouseNames();
        String missingName = normalizedNames.stream()
                .filter(name -> !configuredNames.contains(name))
                .findFirst()
                .orElse(null);
        if (missingName != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "码头不存在: " + missingName);
        }
    }

    public void evictCache() {
        if (redisJsonCacheSupport == null) {
            return;
        }
        redisJsonCacheSupport.delete(WAREHOUSE_CACHE_KEY);
    }

    private Set<String> loadActiveWarehouseNames() {
        if (warehouseRepository == null) {
            return Set.of();
        }
        List<String> names;
        if (redisJsonCacheSupport == null) {
            names = warehouseRepository.findByDeletedFlagFalseOrderByWarehouseNameAsc().stream()
                    .map(warehouse -> warehouse.getWarehouseName() == null ? null : warehouse.getWarehouseName().trim())
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
        } else {
            names = redisJsonCacheSupport.getOrLoad(
                    WAREHOUSE_CACHE_KEY,
                    WAREHOUSE_CACHE_TTL,
                    WAREHOUSE_LIST_TYPE,
                    () -> warehouseRepository.findByDeletedFlagFalseOrderByWarehouseNameAsc().stream()
                            .map(warehouse -> warehouse.getWarehouseName() == null ? null : warehouse.getWarehouseName().trim())
                            .filter(name -> name != null && !name.isBlank())
                            .toList()
            );
        }
        return names.stream().collect(Collectors.toSet());
    }
}
