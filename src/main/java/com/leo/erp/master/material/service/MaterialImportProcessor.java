package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Service
class MaterialImportProcessor {

    private final MaterialRepository materialRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final MaterialIdentityService identityService;

    MaterialImportProcessor(MaterialRepository materialRepository,
                            SnowflakeIdGenerator idGenerator,
                            MaterialIdentityService identityService) {
        this.materialRepository = materialRepository;
        this.idGenerator = idGenerator;
        this.identityService = identityService;
    }

    ImportSession start(Collection<MaterialIdentityService.Identity> identities) {
        return new ImportSession(identityService.activeIndex(identities));
    }

    ImportRowResult importRow(ImportSession session, MaterialImportData data, int rowNumber) {
        String providedMaterialCode = normalizeText(data.materialCode());
        Optional<Material> materialByCode = providedMaterialCode.isBlank()
                ? Optional.empty()
                : materialRepository.findByMaterialCode(providedMaterialCode);
        MaterialIdentityService.Identity identity = identityService.identity(data);
        Material skipCandidate = materialByCode.orElse(session.identityIndex().get(identity));
        if (identityService.isExactImportMatch(skipCandidate, data, materialByCode.isPresent())) {
            return new ImportRowResult(ImportOutcome.SKIPPED, skipCandidate);
        }

        Material material = materialByCode.orElseGet(this::newMaterial);
        boolean exists = materialByCode.isPresent();
        MaterialIdentityService.Identity previousIdentity = identityService.identity(material);
        identityService.validateImport(material, identity, session.identityIndex(), rowNumber);
        material.setDeletedFlag(false);
        apply(material, data);
        try {
            materialRepository.save(material);
        } catch (DataIntegrityViolationException exception) {
            throw identityService.mapViolation(exception, ErrorCode.VALIDATION_ERROR, rowNumber);
        }
        identityService.registerImport(session.identityIndex(), previousIdentity, identity, material);
        return new ImportRowResult(exists ? ImportOutcome.UPDATED : ImportOutcome.CREATED, material);
    }

    MaterialIdentityService.Identity identity(MaterialImportData data) {
        return identityService.identity(data);
    }

    MaterialIdentityService.Identity identity(String brand, String material, String spec, String length) {
        return identityService.identity(brand, material, spec, length);
    }

    private Material newMaterial() {
        Material material = new Material();
        material.setId(idGenerator.nextId());
        return material;
    }

    private void apply(Material material, MaterialImportData data) {
        material.setMaterialCode(resolveMaterialCode(material.getMaterialCode(), material.getId()));
        material.setBrand(data.brand());
        material.setMaterial(data.material());
        material.setCategory(data.category());
        material.setSpec(data.spec());
        material.setLength(data.length());
        material.setUnit(data.unit());
        material.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(data.quantityUnit()));
        material.setPieceWeightTon(data.pieceWeightTon());
        material.setPiecesPerBundle(data.piecesPerBundle() == null ? 0 : data.piecesPerBundle());
        material.setUnitPrice(data.unitPrice() == null ? BigDecimal.ZERO : data.unitPrice());
        material.setRemark(data.remark());
    }

    private String resolveMaterialCode(String currentCode, Long materialId) {
        if (currentCode != null && !currentCode.isBlank()) {
            return currentCode.trim();
        }
        if (materialId == null || materialId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
        }
        return String.valueOf(materialId);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    record ImportSession(Map<MaterialIdentityService.Identity, Material> identityIndex) {
    }

    record ImportRowResult(ImportOutcome outcome, Material material) {
    }

    enum ImportOutcome {
        CREATED,
        UPDATED,
        SKIPPED
    }
}
