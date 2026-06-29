package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.BusinessNumberAllocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class TradeItemMaterialSupport implements RedisCacheHealthCheck {

    private static final String MATERIAL_CACHE_KEY = "leo:material:all";
    private static final Duration MATERIAL_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<TradeMaterialSnapshot>> MATERIAL_LIST_TYPE = new TypeReference<>() { };

    private final MaterialCatalog materialCatalog;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final TradeItemRuntimeSettings tradeItemRuntimeSettings;
    private final BusinessNumberAllocator businessNumberAllocator;

    @Autowired
    public TradeItemMaterialSupport(MaterialCatalog materialCatalog,
                                    RedisJsonCacheSupport redisJsonCacheSupport,
                                    TradeItemRuntimeSettings tradeItemRuntimeSettings,
                                    BusinessNumberAllocator businessNumberAllocator) {
        this.materialCatalog = materialCatalog;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.tradeItemRuntimeSettings = tradeItemRuntimeSettings;
        this.businessNumberAllocator = businessNumberAllocator;
    }

    public TradeItemMaterialSupport(MaterialCatalog materialCatalog) {
        this(materialCatalog, null, null, null);
    }

    public Map<String, TradeMaterialSnapshot> loadMaterialMap(Collection<String> materialCodes) {
        List<String> normalizedCodes = materialCodes == null ? List.of() : materialCodes.stream()
                .map(this::normalizeOptionalMaterialCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedCodes.isEmpty()) {
            return Map.of();
        }

        Map<String, TradeMaterialSnapshot> activeMaterialsByCode = loadActiveMaterialsByCode();
        Map<String, TradeMaterialSnapshot> materialMap = new LinkedHashMap<>();
        normalizedCodes.forEach(code -> {
            TradeMaterialSnapshot material = activeMaterialsByCode.get(code);
            if (material != null) {
                materialMap.put(code, material);
            }
        });

        List<String> missingCodes = normalizedCodes.stream()
                .filter(code -> !materialMap.containsKey(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "商品不存在: " + missingCodes.get(0));
        }
        return materialMap;
    }

    public String normalizeMaterialCode(String materialCode, int lineNo) {
        String normalized = normalizeOptionalMaterialCode(materialCode);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行商品编码不能为空");
        }
        return normalized;
    }

    public String normalizeBatchNo(TradeMaterialSnapshot material, String batchNo, int lineNo, boolean requiredWhenEnabled) {
        String normalized = batchNo == null ? null : batchNo.trim();
        if (normalized != null && normalized.isBlank()) {
            normalized = null;
        }
        if (!isBatchManaged(material)) {
            return null;
        }
        if (shouldAutoGenerateBatchNo()) {
            // 前端已生成雪花 ID 则直接使用，否则后端补生成
            if (normalized == null) {
                long id = SnowflakeIdGenerator.getInstance().nextId();
                normalized = Long.toString(id, 36).toUpperCase();
            }
        }
        if (normalized != null && normalized.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行批号长度不能超过64");
        }
        if (requiredWhenEnabled && normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行当前商品需批号管理，批号不能为空");
        }
        return normalized;
    }

    public boolean normalizeBatchNoEnabled(Boolean batchNoEnabled) {
        return Boolean.TRUE.equals(batchNoEnabled);
    }

    public void evictCache() {
        if (redisJsonCacheSupport == null) {
            return;
        }
        redisJsonCacheSupport.deleteAfterCommit(MATERIAL_CACHE_KEY);
    }

    private Map<String, TradeMaterialSnapshot> loadActiveMaterialsByCode() {
        List<TradeMaterialSnapshot> snapshots;
        if (materialCatalog == null) {
            snapshots = List.of();
        } else if (redisJsonCacheSupport == null) {
            snapshots = materialCatalog.listActiveMaterials();
        } else {
            snapshots = redisJsonCacheSupport.getOrLoad(
                    MATERIAL_CACHE_KEY,
                    MATERIAL_CACHE_TTL,
                    MATERIAL_LIST_TYPE,
                    this::loadActiveMaterialsFromCatalog
            );
            if (snapshots.isEmpty()) {
                List<TradeMaterialSnapshot> refreshed = loadActiveMaterialsFromCatalog();
                if (refreshed.isEmpty()) {
                    return Map.of();
                }
                writeMaterialCache(refreshed);
                snapshots = refreshed;
            }
        }

        Map<String, TradeMaterialSnapshot> materialsByCode = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> {
            String materialCode = normalizeOptionalMaterialCode(snapshot.materialCode());
            if (materialCode != null) {
                materialsByCode.put(materialCode, snapshot);
            }
        });
        return materialsByCode;
    }

    private List<TradeMaterialSnapshot> loadActiveMaterialsFromCatalog() {
        if (materialCatalog == null) {
            return List.of();
        }
        return materialCatalog.listActiveMaterials();
    }

    private void writeMaterialCache(List<TradeMaterialSnapshot> snapshots) {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.write(MATERIAL_CACHE_KEY, snapshots, MATERIAL_CACHE_TTL);
        }
    }

    @Override
    public String cacheName() {
        return MATERIAL_CACHE_KEY;
    }

    @Override
    public CacheHealthCheckResult verifyAndRefreshCache() {
        List<TradeMaterialSnapshot> expected = loadActiveMaterialsFromCatalog();
        return verifyAndRefreshListCache(
                redisJsonCacheSupport,
                MATERIAL_CACHE_KEY,
                MATERIAL_CACHE_TTL,
                MATERIAL_LIST_TYPE,
                expected
        );
    }

    private boolean shouldAutoGenerateBatchNo() {
        return tradeItemRuntimeSettings != null
                && businessNumberAllocator != null
                && tradeItemRuntimeSettings.shouldAutoGenerateBatchNo();
    }

    private boolean isBatchManaged(TradeMaterialSnapshot material) {
        return Boolean.TRUE.equals(material.batchNoEnabled()) || shouldForceBatchManagement();
    }

    private boolean shouldForceBatchManagement() {
        return tradeItemRuntimeSettings != null && tradeItemRuntimeSettings.shouldForceBatchManagement();
    }

    private String normalizeOptionalMaterialCode(String materialCode) {
        if (materialCode == null) {
            return null;
        }
        String normalized = materialCode.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
