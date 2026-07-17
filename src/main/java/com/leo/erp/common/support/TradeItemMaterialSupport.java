package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class TradeItemMaterialSupport implements RedisCacheHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(TradeItemMaterialSupport.class);
    private static final String MATERIAL_CACHE_KEY = "leo:material:all";
    private static final Duration MATERIAL_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<TradeMaterialSnapshot>> MATERIAL_LIST_TYPE = new TypeReference<>() { };

    private final MaterialCatalog materialCatalog;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final TradeItemRuntimeSettings tradeItemRuntimeSettings;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    public TradeItemMaterialSupport(MaterialCatalog materialCatalog,
                                    RedisJsonCacheSupport redisJsonCacheSupport,
                                    TradeItemRuntimeSettings tradeItemRuntimeSettings,
                                    SnowflakeIdGenerator snowflakeIdGenerator) {
        this.materialCatalog = materialCatalog;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.tradeItemRuntimeSettings = tradeItemRuntimeSettings;
        this.snowflakeIdGenerator = Objects.requireNonNull(snowflakeIdGenerator,
                "SnowflakeIdGenerator must not be null");
    }

    public TradeItemMaterialSupport(MaterialCatalog materialCatalog) {
        this(materialCatalog, null, null, new SnowflakeIdGenerator(0L));
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

    public TradeMaterialSnapshot resolveMaterial(Long materialId, String materialCode, int lineNo) {
        String normalizedCode = normalizeMaterialCode(materialCode, lineNo);
        if (materialId != null) {
            TradeMaterialSnapshot resolved = loadActiveMaterialsFromCatalog().stream()
                    .filter(material -> materialId.equals(material.materialId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BUSINESS_ERROR,
                            "第" + lineNo + "行商品不存在或已停用"
                    ));
            if (!normalizedCode.equals(normalizeOptionalMaterialCode(resolved.materialCode()))) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "第" + lineNo + "行商品ID与编码不一致");
            }
            return resolved;
        }

        log.warn("identity_fallback module=trade-item field=materialId line={} reason=material-code", lineNo);
        return loadMaterialMap(List.of(normalizedCode)).get(normalizedCode);
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
        if (normalized == null) {
            normalized = String.valueOf(snowflakeIdGenerator.nextId());
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
        List<TradeMaterialSnapshot> snapshots = loadActiveMaterialsFromCatalog();

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
