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
import java.util.Objects;
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
        return resolveWarehouse(null, warehouseName, lineNo, required).warehouseName();
    }

    public WarehouseSnapshot resolveWarehouse(Long warehouseId,
                                               String warehouseName,
                                               int lineNo,
                                               boolean required) {
        String trimmedName = warehouseName == null ? null : warehouseName.trim();
        String normalized = trimmedName == null || trimmedName.isBlank() ? null : trimmedName;
        if (required && normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行码头不能为空");
        }
        if (warehouseId == null && normalized == null) {
            return new WarehouseSnapshot(null, null, null);
        }
        if (normalized != null && normalized.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行码头长度不能超过128");
        }
        List<WarehouseSnapshot> warehouses = loadActiveWarehouses();
        if (warehouseId != null) {
            WarehouseSnapshot resolved = warehouses.stream()
                    .filter(warehouse -> warehouseId.equals(warehouse.warehouseId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BUSINESS_ERROR,
                            "第" + lineNo + "行仓库不存在或已停用"
                    ));
            if (normalized != null && !normalized.equals(resolved.warehouseName())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "第" + lineNo + "行仓库ID与名称不一致");
            }
            return resolved;
        }

        List<WarehouseSnapshot> candidates = warehouses.stream()
                .filter(warehouse -> Objects.equals(normalized, warehouse.warehouseName()))
                .toList();
        if (candidates.size() != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    candidates.isEmpty() ? "码头不存在: " + normalized : "码头名称不唯一: " + normalized);
        }
        return candidates.get(0);
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
        return loadActiveWarehouses().stream()
                .filter(warehouse -> warehouse.warehouseName() != null && !warehouse.warehouseName().isBlank())
                .map(warehouse -> new OptionResponse(
                        warehouse.warehouseCode() == null || warehouse.warehouseCode().isBlank()
                                ? warehouse.warehouseName()
                                : warehouse.warehouseCode() + " / " + warehouse.warehouseName(),
                        warehouse.warehouseId() == null
                                ? warehouse.warehouseName()
                                : warehouse.warehouseId().toString()
                ))
                .toList();
    }

    private List<WarehouseSnapshot> loadActiveWarehouses() {
        if (warehouseCatalog == null) {
            return List.of();
        }
        List<WarehouseSnapshot> snapshots = warehouseCatalog.listActiveWarehouses();
        if (snapshots == null || snapshots.isEmpty()) {
            snapshots = warehouseCatalog.listActiveWarehouseNames().stream()
                    .map(name -> new WarehouseSnapshot(null, null, name))
                    .toList();
        }
        return snapshots.stream()
                .filter(Objects::nonNull)
                .map(warehouse -> new WarehouseSnapshot(
                        warehouse.warehouseId(),
                        warehouse.warehouseCode() == null ? null : warehouse.warehouseCode().trim(),
                        warehouse.warehouseName() == null ? null : warehouse.warehouseName().trim()
                ))
                .toList();
    }

    private List<String> loadActiveWarehouseNames() {
        return loadActiveWarehouseNamesFromCatalog();
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
