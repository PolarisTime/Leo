package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.OptionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WarehouseSelectionSupport implements RedisCacheHealthCheck {

    private static final String WAREHOUSE_CACHE_KEY = "leo:warehouse:all";
    private static final Duration WAREHOUSE_CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<String>> WAREHOUSE_LIST_TYPE = new TypeReference<>() { };

    private final WarehouseCatalog warehouseCatalog;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public WarehouseSelectionSupport(WarehouseCatalog warehouseCatalog, RedisJsonCacheSupport redisJsonCacheSupport) {
        this.warehouseCatalog = warehouseCatalog;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public WarehouseSelectionSupport(WarehouseCatalog warehouseCatalog) {
        this(warehouseCatalog, null);
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
        Set<String> configuredNames = loadActiveWarehouseNameSet();
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
        redisJsonCacheSupport.deleteAfterCommit(WAREHOUSE_CACHE_KEY);
    }

    public List<OptionResponse> listActiveOptions() {
        return loadActiveWarehouseNames().stream()
                .map(name -> new OptionResponse(name, name))
                .toList();
    }

    private List<String> loadActiveWarehouseNames() {
        if (warehouseCatalog == null) {
            return List.of();
        }
        List<String> names;
        if (redisJsonCacheSupport == null) {
            names = loadActiveWarehouseNamesFromCatalog();
        } else {
            names = redisJsonCacheSupport.getOrLoad(
                    WAREHOUSE_CACHE_KEY,
                    WAREHOUSE_CACHE_TTL,
                    WAREHOUSE_LIST_TYPE,
                    this::loadActiveWarehouseNamesFromCatalog
            );
            if (names.isEmpty()) {
                List<String> refreshed = loadActiveWarehouseNamesFromCatalog();
                if (refreshed.isEmpty()) {
                    return List.of();
                }
                writeActiveWarehouseNameCache(refreshed);
                names = refreshed;
            }
        }
        return normalizeWarehouseNames(names);
    }

    private List<String> loadActiveWarehouseNamesFromCatalog() {
        if (warehouseCatalog == null) {
            return List.of();
        }
        return normalizeWarehouseNames(warehouseCatalog.listActiveWarehouseNames());
    }

    private List<String> normalizeWarehouseNames(List<String> names) {
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .toList();
    }

    private void writeActiveWarehouseNameCache(List<String> names) {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.write(WAREHOUSE_CACHE_KEY, names, WAREHOUSE_CACHE_TTL);
        }
    }

    @Override
    public String cacheName() {
        return WAREHOUSE_CACHE_KEY;
    }

    @Override
    public CacheHealthCheckResult verifyAndRefreshCache() {
        List<String> expected = loadActiveWarehouseNamesFromCatalog();
        return verifyAndRefreshListCache(
                redisJsonCacheSupport,
                WAREHOUSE_CACHE_KEY,
                WAREHOUSE_CACHE_TTL,
                WAREHOUSE_LIST_TYPE,
                expected
        );
    }

    private Set<String> loadActiveWarehouseNameSet() {
        return loadActiveWarehouseNames().stream().collect(Collectors.toSet());
    }
}
