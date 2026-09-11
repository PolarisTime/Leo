package com.leo.erp.master.material.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.mapper.MaterialMapper;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class MaterialService {

    private static final String CODE_MODULE_KEY = "material";
    private static final CrudStatusGuard<Material> STATUS_GUARD = CrudStatusGuard.withoutStatus();
    private static final Set<StatusTransition> NO_STATUS_TRANSITIONS = Set.of();

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(MaterialService.class);
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final MaterialRepository materialRepository;
    private final MaterialMapper materialMapper;
    private final MaterialReferenceGuard materialReferenceGuard;
    private final MasterDataCodeIssuanceService codeIssuanceService;
    private final MaterialIdentityService identityService;
    private final com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService;

    public MaterialService(MaterialRepository materialRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           MaterialMapper materialMapper,
                           MaterialReferenceGuard materialReferenceGuard,
                           MasterDataCodeIssuanceService codeIssuanceService,
                           MaterialIdentityService identityService,
                           com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.materialRepository = materialRepository;
        this.materialMapper = materialMapper;
        this.materialReferenceGuard = materialReferenceGuard;
        this.codeIssuanceService = codeIssuanceService;
        this.identityService = identityService;
        this.referenceSnapshotSyncService = referenceSnapshotSyncService;
    }

    @Transactional(readOnly = true)
    public MaterialResponse detail(Long id) {
        return toResponse(requireActiveMaterial(id));
    }

    @Transactional
    public MaterialResponse create(MaterialRequest request) {
        Material entity = new Material();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        validateCreate(request);
        apply(entity, request);
        Material saved = saveCreatedMaterial(entity);
        operationLogger.created(entity, entityId);
        return toResponse(saved);
    }

    @Transactional
    public MaterialResponse update(Long id, MaterialRequest request) {
        Material entity = requireActiveMaterial(id);
        String currentBrand = entity.getBrand();
        validateUpdate(entity, request);
        apply(entity, request);
        Material saved = saveMaterial(entity);
        MaterialResponse response = toResponse(saved);
        operationLogger.updated(entity, id);
        // 品牌变更同步引用快照；附加费用类无品牌语义，不触发。
        if (!request.isExpense() && !currentBrand.equals(request.brand())) {
            referenceSnapshotSyncService.syncMaterialName(id, request.brand());
        }
        return response;
    }

    @Transactional
    public MaterialResponse updateStatus(Long id, String status) {
        Material entity = requireActiveMaterial(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(NO_STATUS_TRANSITIONS, currentStatus, nextStatus);
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持状态变更");
    }

    @Transactional
    public void delete(Long id) {
        Material entity = requireActiveMaterial(id);
        materialReferenceGuard.assertNoReferences(entity);
        entity.setDeletedFlag(true);
        saveMaterial(entity);
        operationLogger.deleted(entity, id);
    }

    @Transactional(readOnly = true)
    public Page<MaterialResponse> page(PageQuery query, String keyword, String category, String material,
                                       String materialType) {
        Pageable pageable = query.sortBy() != null
                ? query.toPageable("id")
                : PageRequest.of(query.page(), query.size(), MaterialSearchPolicy.DEFAULT_SORT);
        return materialRepository.findAll(
                MaterialSearchPolicy.page(keyword, category, material, materialType), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<String> materialGrades() {
        return materialRepository.findDistinctMaterials();
    }

    @Transactional(readOnly = true)
    public List<String> materialBrands() {
        return materialRepository.findDistinctActiveProductBrands();
    }

    private Material requireActiveMaterial(Long id) {
        return materialRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));
    }

    private void validateCreate(MaterialRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.materialCode());
        identityService.ensureUnique(null, identity(request));
    }

    private void validateUpdate(Material entity, MaterialRequest request) {
        identityService.ensureUnique(entity.getId(), identity(request));
    }

    private void apply(Material entity, MaterialRequest request) {
        entity.setMaterialCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getMaterialCode(),
                request.materialCode()
        ));
        if (request.isExpense()) {
            applyExpense(entity, request);
            return;
        }
        entity.setMaterialType(MaterialRequest.TYPE_PHYSICAL);
        entity.setBrand(requireText(request.brand(), "品牌不能为空"));
        entity.setMaterial(request.material());
        entity.setCategory(request.category());
        entity.setSpec(requireText(request.spec(), "规格不能为空"));
        entity.setLength(request.length());
        entity.setUnit(request.unit());
        entity.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(request.quantityUnit()));
        entity.setPieceWeightTon(requireWeight(request.pieceWeightTon()));
        entity.setPiecesPerBundle(request.piecesPerBundle() == null ? 0 : request.piecesPerBundle());
        entity.setUnitPrice(request.unitPrice() == null ? BigDecimal.ZERO : request.unitPrice());
        entity.setRemark(request.remark());
    }

    /** 附加费用类：物理属性列存空串/零值，名称即品名，类别固定"附加费用"。 */
    private void applyExpense(Material entity, MaterialRequest request) {
        entity.setMaterialType(MaterialRequest.TYPE_EXPENSE);
        entity.setBrand("");
        entity.setMaterial(request.material());
        entity.setCategory("附加费用");
        entity.setSpec("");
        entity.setLength("");
        entity.setUnit(request.unit());
        entity.setQuantityUnit(request.unit());
        entity.setPieceWeightTon(BigDecimal.ZERO);
        entity.setPiecesPerBundle(0);
        entity.setUnitPrice(request.unitPrice() == null ? BigDecimal.ZERO : request.unitPrice());
        entity.setRemark(request.remark());
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }

    private BigDecimal requireWeight(BigDecimal value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "件重不能为空");
        }
        return value;
    }

    private Material saveMaterial(Material entity) {
        try {
            return materialRepository.save(entity);
        } catch (DataIntegrityViolationException exception) {
            throw identityService.mapViolation(exception, ErrorCode.BUSINESS_ERROR, null);
        }
    }

    private Material saveCreatedMaterial(Material entity) {
        Material saved = saveMaterial(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getMaterialCode());
        return saved;
    }

    private MaterialResponse toResponse(Material entity) {
        return materialMapper.toResponse(entity);
    }

    private MaterialIdentityService.Identity identity(MaterialRequest request) {
        return identityService.identity(request.brand(), request.material(), request.spec(), request.length());
    }
}
