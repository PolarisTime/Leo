package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.material.domain.MaterialSnapshot;
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
    private final MaterialHistoryRecorder historyRecorder;

    MaterialImportProcessor(MaterialRepository materialRepository,
                            SnowflakeIdGenerator idGenerator,
                            MaterialIdentityService identityService,
                            MaterialHistoryRecorder historyRecorder) {
        this.materialRepository = materialRepository;
        this.idGenerator = idGenerator;
        this.identityService = identityService;
        this.historyRecorder = historyRecorder;
    }

    ImportSession start(Collection<MaterialIdentityService.Identity> identities) {
        // 一次导入生成一个批次号，用于写入历史并在需要时按批次回滚。
        String batchNo = String.valueOf(idGenerator.nextId());
        return new ImportSession(identityService.activeIndex(identities), batchNo);
    }

    ImportRowResult importRow(ImportSession session, MaterialImportData data, int rowNumber) {
        String providedMaterialCode = normalizeText(data.materialCode());
        Optional<Material> materialByCode = providedMaterialCode.isBlank()
                ? Optional.empty()
                : materialRepository.findByMaterialCode(providedMaterialCode);
        MaterialIdentityService.Identity identity = identityService.identity(data);
        // 商品编码或身份（品牌、材质、规格、长度）命中已有商品时按更新处理，导入即批量维护主数据。
        Material material = materialByCode.orElse(session.identityIndex().get(identity));
        if (identityService.isExactImportMatch(material, data, materialByCode.isPresent())) {
            return new ImportRowResult(ImportOutcome.SKIPPED, material);
        }

        boolean exists = material != null;
        if (!exists) {
            material = newMaterial();
        }
        MaterialIdentityService.Identity previousIdentity = identityService.identity(material);
        MaterialSnapshot before = exists ? MaterialSnapshot.of(material) : null;
        identityService.validateImport(material, identity, session.identityIndex(), rowNumber);
        material.setDeletedFlag(false);
        apply(material, data);
        try {
            materialRepository.save(material);
        } catch (DataIntegrityViolationException exception) {
            throw identityService.mapViolation(exception, ErrorCode.VALIDATION_ERROR, rowNumber);
        }
        identityService.registerImport(session.identityIndex(), previousIdentity, identity, material);
        historyRecorder.record(material.getId(), MaterialHistoryRecorder.SOURCE_IMPORT,
                exists ? MaterialHistoryRecorder.TYPE_UPDATED : MaterialHistoryRecorder.TYPE_CREATED,
                before, MaterialSnapshot.of(material), session.batchNo(), null);
        return new ImportRowResult(exists ? ImportOutcome.UPDATED : ImportOutcome.CREATED, material);
    }

    /**
     * 预览单行导入结果：不落库、不写历史，只返回判定结果与前后字段差异。
     * 为与正式导入行为一致，预览同样按顺序在会话索引中登记工作副本，但不触碰数据库实体。
     */
    MaterialPreview previewRow(ImportSession session, MaterialImportData data, int rowNumber) {
        String providedMaterialCode = normalizeText(data.materialCode());
        Optional<Material> materialByCode = providedMaterialCode.isBlank()
                ? Optional.empty()
                : materialRepository.findByMaterialCode(providedMaterialCode);
        MaterialIdentityService.Identity identity = identityService.identity(data);
        Material existing = materialByCode.orElse(session.identityIndex().get(identity));
        if (identityService.isExactImportMatch(existing, data, materialByCode.isPresent())) {
            MaterialSnapshot current = MaterialSnapshot.of(existing);
            return new MaterialPreview(ImportOutcome.SKIPPED, current, current, existing.getId());
        }

        boolean exists = existing != null;
        Material working = new Material();
        if (exists) {
            MaterialSnapshot.of(existing).applyTo(working);
        } else {
            working.setId(idGenerator.nextId());
        }
        MaterialIdentityService.Identity previousIdentity = identityService.identity(working);
        identityService.validateImport(working, identity, session.identityIndex(), rowNumber);
        working.setDeletedFlag(false);
        apply(working, data);
        identityService.registerImport(session.identityIndex(), previousIdentity, identity, working);
        return new MaterialPreview(
                exists ? ImportOutcome.UPDATED : ImportOutcome.CREATED,
                exists ? MaterialSnapshot.of(existing) : null,
                MaterialSnapshot.of(working),
                exists ? existing.getId() : null
        );
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
        material.setMaterialType(data.isExpense()
                ? MaterialImportData.TYPE_EXPENSE
                : MaterialImportData.TYPE_PHYSICAL);
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

    record ImportSession(Map<MaterialIdentityService.Identity, Material> identityIndex, String batchNo) {
    }

    record ImportRowResult(ImportOutcome outcome, Material material) {
    }

    record MaterialPreview(ImportOutcome outcome, MaterialSnapshot before, MaterialSnapshot after, Long materialId) {
    }

    enum ImportOutcome {
        CREATED,
        UPDATED,
        SKIPPED
    }
}
