package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.system.norule.service.NoRuleSequenceService;
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class TradeItemMaterialSupport {

    private static final String MATERIAL_CACHE_KEY = "leo:material:all";
    private static final Duration MATERIAL_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<MaterialSnapshot>> MATERIAL_LIST_TYPE = new TypeReference<>() { };

    private final MaterialRepository materialRepository;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final SystemSwitchService systemSwitchService;
    private final NoRuleSequenceService noRuleSequenceService;

    @Autowired
    public TradeItemMaterialSupport(MaterialRepository materialRepository,
                                    RedisJsonCacheSupport redisJsonCacheSupport,
                                    SystemSwitchService systemSwitchService,
                                    NoRuleSequenceService noRuleSequenceService) {
        this.materialRepository = materialRepository;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.systemSwitchService = systemSwitchService;
        this.noRuleSequenceService = noRuleSequenceService;
    }

    public TradeItemMaterialSupport(MaterialRepository materialRepository) {
        this(materialRepository, null, null, null);
    }

    public Map<String, Material> loadMaterialMap(Collection<String> materialCodes) {
        List<String> normalizedCodes = materialCodes == null ? List.of() : materialCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
        if (normalizedCodes.isEmpty()) {
            return Map.of();
        }

        Map<String, Material> activeMaterialsByCode = loadActiveMaterialsByCode();
        Map<String, Material> materialMap = new LinkedHashMap<>();
        normalizedCodes.forEach(code -> {
            Material material = activeMaterialsByCode.get(code);
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

    public String normalizeBatchNo(Material material, String batchNo, int lineNo, boolean requiredWhenEnabled) {
        String normalized = batchNo == null ? null : batchNo.trim();
        if (normalized != null && normalized.isBlank()) {
            normalized = null;
        }
        if (!Boolean.TRUE.equals(material.getBatchNoEnabled())) {
            return null;
        }
        if (normalized == null && shouldAutoGenerateBatchNo()) {
            normalized = noRuleSequenceService.nextValue(NoRuleSequenceService.BATCH_NO_RULE_CODE);
        }
        if (normalized != null && normalized.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行批号长度不能超过64");
        }
        if (requiredWhenEnabled && normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行商品已启用批号管理，批号不能为空");
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
        redisJsonCacheSupport.delete(MATERIAL_CACHE_KEY);
    }

    private Map<String, Material> loadActiveMaterialsByCode() {
        List<MaterialSnapshot> snapshots;
        if (materialRepository == null) {
            snapshots = List.of();
        } else if (redisJsonCacheSupport == null) {
            snapshots = materialRepository.findByDeletedFlagFalseOrderByMaterialCodeAsc().stream()
                    .map(material -> new MaterialSnapshot(material.getMaterialCode(), Boolean.TRUE.equals(material.getBatchNoEnabled())))
                    .toList();
        } else {
            snapshots = redisJsonCacheSupport.getOrLoad(
                    MATERIAL_CACHE_KEY,
                    MATERIAL_CACHE_TTL,
                    MATERIAL_LIST_TYPE,
                    () -> materialRepository.findByDeletedFlagFalseOrderByMaterialCodeAsc().stream()
                            .map(material -> new MaterialSnapshot(material.getMaterialCode(), Boolean.TRUE.equals(material.getBatchNoEnabled())))
                            .toList()
            );
        }

        Map<String, Material> materialsByCode = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> {
            Material material = new Material();
            material.setMaterialCode(snapshot.materialCode());
            material.setBatchNoEnabled(snapshot.batchNoEnabled());
            materialsByCode.put(snapshot.materialCode(), material);
        });
        return materialsByCode;
    }

    private boolean shouldAutoGenerateBatchNo() {
        return systemSwitchService != null
                && noRuleSequenceService != null
                && systemSwitchService.shouldAutoGenerateBatchNo();
    }

    private record MaterialSnapshot(String materialCode, Boolean batchNoEnabled) {
    }
}
