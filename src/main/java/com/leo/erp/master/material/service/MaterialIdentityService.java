package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
class MaterialIdentityService {

    private static final String MATERIAL_IDENTITY_UNIQUE_INDEX = "uk_md_material_identity_active";

    private final MaterialRepository materialRepository;

    MaterialIdentityService(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }

    Identity identity(String brand, String material, String spec, String length) {
        return new Identity(brand, material, spec, length);
    }

    Identity identity(MaterialImportData data) {
        return identity(data.brand(), data.material(), data.spec(), data.length());
    }

    Identity identity(Material material) {
        return identity(material.getBrand(), material.getMaterial(), material.getSpec(), material.getLength());
    }

    void ensureUnique(Long excludedId, Identity identity) {
        List<Material> conflicts = materialRepository.findActiveIdentityConflicts(
                identity.brand(), identity.material(), identity.spec(), identity.length(), excludedId
        );
        if (conflicts != null && !conflicts.isEmpty()) {
            throw duplicateException(ErrorCode.BUSINESS_ERROR, conflicts.getFirst(), null);
        }
    }

    Map<Identity, Material> activeIndex(Collection<Identity> identities) {
        Map<Identity, Material> index = new LinkedHashMap<>();
        if (identities == null || identities.isEmpty()) {
            return index;
        }
        List<String> brands = identityValues(identities, Identity::brand);
        List<String> materials = identityValues(identities, Identity::material);
        List<String> specs = identityValues(identities, Identity::spec);
        if (brands.isEmpty() || materials.isEmpty() || specs.isEmpty()) {
            return index;
        }
        List<Material> candidates = materialRepository.findActiveIdentityCandidates(brands, materials, specs);
        if (candidates == null) {
            return index;
        }
        for (Material material : candidates) {
            index.putIfAbsent(identity(material), material);
        }
        return index;
    }

    void validateImport(Material importingMaterial,
                        Identity identity,
                        Map<Identity, Material> identityIndex,
                        int rowNumber) {
        Material duplicate = identityIndex.get(identity);
        if (duplicate != null && !sameMaterial(duplicate, importingMaterial)) {
            throw duplicateException(ErrorCode.VALIDATION_ERROR, duplicate, rowNumber);
        }
    }

    void registerImport(Map<Identity, Material> identityIndex,
                        Identity previousIdentity,
                        Identity identity,
                        Material material) {
        if (!previousIdentity.equals(identity) && sameMaterial(identityIndex.get(previousIdentity), material)) {
            identityIndex.remove(previousIdentity);
        }
        identityIndex.put(identity, material);
    }

    boolean isExactImportMatch(Material existing, MaterialImportData data, boolean compareCode) {
        return existing != null
                && !existing.isDeletedFlag()
                && (!compareCode || sameText(existing.getMaterialCode(), data.materialCode()))
                && sameText(existing.getBrand(), data.brand())
                && sameText(existing.getMaterial(), data.material())
                && sameText(existing.getCategory(), data.category())
                && sameText(existing.getSpec(), data.spec())
                && sameText(existing.getLength(), data.length())
                && sameText(existing.getUnit(), data.unit())
                && sameText(
                existing.getQuantityUnit(),
                TradeItemCalculator.normalizeQuantityUnit(normalizeText(data.quantityUnit()))
        )
                && sameDecimal(existing.getPieceWeightTon(), data.pieceWeightTon())
                && sameInteger(existing.getPiecesPerBundle(), data.piecesPerBundle())
                && sameDecimal(existing.getUnitPrice(), data.unitPrice())
                && sameText(existing.getRemark(), data.remark())
                && sameMaterialType(existing.getMaterialType(), data);
    }

    /** 商品类型比较：存量行为空视为实体商品（V122 回填前的历史数据）。 */
    private boolean sameMaterialType(String existingType, MaterialImportData data) {
        String normalized = existingType == null || existingType.isBlank()
                ? MaterialImportData.TYPE_PHYSICAL
                : existingType.trim();
        return normalized.equals(data.isExpense()
                ? MaterialImportData.TYPE_EXPENSE
                : MaterialImportData.TYPE_PHYSICAL);
    }

    BusinessException mapViolation(DataIntegrityViolationException exception,
                                   ErrorCode errorCode,
                                   Integer rowNumber) {
        if (isIdentityViolation(exception)) {
            return duplicateException(errorCode, null, rowNumber);
        }
        throw exception;
    }

    private List<String> identityValues(Collection<Identity> identities,
                                        Function<Identity, String> mapper) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Identity identity : identities) {
            String value = mapper.apply(identity);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private boolean sameMaterial(Material first, Material second) {
        if (first == null || second == null) {
            return false;
        }
        Long firstId = first.getId();
        Long secondId = second.getId();
        return firstId != null && firstId.equals(secondId);
    }

    private BusinessException duplicateException(ErrorCode errorCode,
                                                 Material duplicate,
                                                 Integer rowNumber) {
        String subject = rowNumber == null ? "商品资料重复" : "第" + rowNumber + "行商品资料重复";
        String duplicateName = duplicate == null
                ? "已有商品"
                : "商品【" + safe(duplicate.getMaterialCode()) + "】";
        return new BusinessException(
                errorCode,
                subject + "：品牌、材质、规格、长度与" + duplicateName + "一致"
        );
    }

    private boolean isIdentityViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains(MATERIAL_IDENTITY_UNIQUE_INDEX)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean sameText(String first, String second) {
        return normalizeText(first).equals(normalizeText(second));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean sameDecimal(BigDecimal first, BigDecimal second) {
        BigDecimal left = first == null ? BigDecimal.ZERO : first;
        BigDecimal right = second == null ? BigDecimal.ZERO : second;
        return left.compareTo(right) == 0;
    }

    private boolean sameInteger(Integer first, Integer second) {
        int left = first == null ? 0 : first;
        int right = second == null ? 0 : second;
        return left == right;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    record Identity(String brand, String material, String spec, String length) {

        Identity {
            brand = normalize(brand);
            material = normalize(material);
            spec = normalize(spec);
            length = normalize(length);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
