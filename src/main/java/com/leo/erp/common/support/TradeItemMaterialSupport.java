package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class TradeItemMaterialSupport {

    private static final Logger log = LoggerFactory.getLogger(TradeItemMaterialSupport.class);

    private final MaterialCatalog materialCatalog;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    public TradeItemMaterialSupport(MaterialCatalog materialCatalog,
                                    SnowflakeIdGenerator snowflakeIdGenerator) {
        this.materialCatalog = materialCatalog;
        this.snowflakeIdGenerator = Objects.requireNonNull(snowflakeIdGenerator,
                "SnowflakeIdGenerator must not be null");
    }

    public TradeItemMaterialSupport(MaterialCatalog materialCatalog) {
        this(materialCatalog, new SnowflakeIdGenerator(0L));
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
        return prepareResolver().resolve(materialId, materialCode, lineNo);
    }

    /**
     * 请求级商品解析器: 只加载一次商品目录快照, 供同一请求的明细循环复用。
     * <p>避免逐行调用 {@link #resolveMaterial} 时重复全量查询商品目录(复杂度 O(行数 × 商品总数)),
     * 同时保持原有的存在性、停用与 ID/编码一致性校验语义。
     */
    public MaterialResolver prepareResolver() {
        Map<Long, TradeMaterialSnapshot> byId = new LinkedHashMap<>();
        Map<String, TradeMaterialSnapshot> byCode = new LinkedHashMap<>();
        for (TradeMaterialSnapshot snapshot : loadActiveMaterialsFromCatalog()) {
            if (snapshot.materialId() != null) {
                byId.putIfAbsent(snapshot.materialId(), snapshot);
            }
            String code = normalizeOptionalMaterialCode(snapshot.materialCode());
            if (code != null) {
                byCode.putIfAbsent(code, snapshot);
            }
        }
        return new CachingMaterialResolver(byId, byCode);
    }

    /** 单次请求内复用的商品解析器; 目录快照在创建时固定。 */
    private final class CachingMaterialResolver implements MaterialResolver {

        private final Map<Long, TradeMaterialSnapshot> byId;
        private final Map<String, TradeMaterialSnapshot> byCode;

        private CachingMaterialResolver(Map<Long, TradeMaterialSnapshot> byId,
                                        Map<String, TradeMaterialSnapshot> byCode) {
            this.byId = byId;
            this.byCode = byCode;
        }

        @Override
        public TradeMaterialSnapshot resolve(Long materialId, String materialCode, int lineNo) {
            String normalizedCode = normalizeMaterialCode(materialCode, lineNo);
            if (materialId != null) {
                TradeMaterialSnapshot resolved = byId.get(materialId);
                if (resolved == null) {
                    throw new BusinessException(
                            ErrorCode.BUSINESS_ERROR,
                            "第" + lineNo + "行商品不存在或已停用"
                    );
                }
                if (!normalizedCode.equals(normalizeOptionalMaterialCode(resolved.materialCode()))) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "第" + lineNo + "行商品ID与编码不一致");
                }
                return resolved;
            }

            log.warn("identity_fallback module=trade-item field=materialId line={} reason=material-code", lineNo);
            TradeMaterialSnapshot resolved = byCode.get(normalizedCode);
            if (resolved == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "商品不存在: " + normalizedCode);
            }
            return resolved;
        }
    }

    public String normalizeMaterialCode(String materialCode, int lineNo) {
        String normalized = normalizeOptionalMaterialCode(materialCode);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行商品编码不能为空");
        }
        return normalized;
    }

    public String normalizeBatchNo(String batchNo, int lineNo) {
        String normalized = normalizeOptionalBatchNo(batchNo);
        if (normalized == null) {
            normalized = String.valueOf(snowflakeIdGenerator.nextId());
        }
        return validateBatchNo(normalized, lineNo);
    }

    public String normalizeRequiredBatchNo(String batchNo, int lineNo) {
        String normalized = normalizeOptionalBatchNo(batchNo);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行批号不能为空");
        }
        return validateBatchNo(normalized, lineNo);
    }

    private String validateBatchNo(String batchNo, int lineNo) {
        String normalized = batchNo.trim();
        if (normalized.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行批号长度不能超过64");
        }
        return normalized;
    }

    private String normalizeOptionalBatchNo(String batchNo) {
        if (batchNo == null) {
            return null;
        }
        String normalized = batchNo.trim();
        return normalized.isBlank() ? null : normalized;
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

    private String normalizeOptionalMaterialCode(String materialCode) {
        if (materialCode == null) {
            return null;
        }
        String normalized = materialCode.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
