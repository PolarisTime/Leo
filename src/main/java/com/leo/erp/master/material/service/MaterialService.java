package com.leo.erp.master.material.service;

import com.leo.erp.master.material.domain.MaterialTypes;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.material.domain.MaterialSnapshot;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.mapper.MaterialMapper;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import org.springframework.cache.annotation.Cacheable;
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

    private static final String CODE_MODULE_KEY = ModuleKeys.MATERIAL;
    private static final CrudStatusGuard<Material> STATUS_GUARD = CrudStatusGuard.withoutStatus();
    /**
     * 商品启停迁移：与其它模块「单向、禁止回退」不同，商品允许「正常↔禁用」双向切换
     * （禁用是运营纠错动作，误禁用必须能立即恢复）。
     */
    private static final Set<StatusTransition> ACTIVE_STATUS_TRANSITIONS = Set.of(
            StatusTransition.of(StatusConstants.NORMAL, StatusConstants.DISABLED),
            StatusTransition.of(StatusConstants.DISABLED, StatusConstants.NORMAL));

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(MaterialService.class);
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final MaterialRepository materialRepository;
    private final MaterialMapper materialMapper;
    private final MaterialReferenceGuard materialReferenceGuard;
    private final MasterDataCodeIssuanceService codeIssuanceService;
    private final MaterialIdentityService identityService;
    private final MaterialHistoryRecorder materialHistoryRecorder;
    private final MaterialDictionaryCache materialDictionaryCache;
    private final MaterialCategoryGuard materialCategoryGuard;

    public MaterialService(MaterialRepository materialRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           MaterialMapper materialMapper,
                           MaterialReferenceGuard materialReferenceGuard,
                           MasterDataCodeIssuanceService codeIssuanceService,
                           MaterialIdentityService identityService,
                           MaterialHistoryRecorder materialHistoryRecorder,
                           MaterialDictionaryCache materialDictionaryCache,
                           MaterialCategoryGuard materialCategoryGuard) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.materialRepository = materialRepository;
        this.materialMapper = materialMapper;
        this.materialReferenceGuard = materialReferenceGuard;
        this.codeIssuanceService = codeIssuanceService;
        this.identityService = identityService;
        this.materialHistoryRecorder = materialHistoryRecorder;
        this.materialDictionaryCache = materialDictionaryCache;
        this.materialCategoryGuard = materialCategoryGuard;
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
        // 新建一律「正常」：禁用只能作用于既有商品，避免导入/前端在创建时写入禁用态。
        entity.setStatus(StatusConstants.NORMAL);
        Material saved = saveCreatedMaterial(entity);
        operationLogger.created(entity, entityId);
        materialHistoryRecorder.record(saved.getId(), MaterialHistoryRecorder.SOURCE_MANUAL,
                MaterialHistoryRecorder.TYPE_CREATED, null, MaterialSnapshot.of(saved), null, null);
        materialDictionaryCache.evictAll();
        return toResponse(saved);
    }

    @Transactional
    public MaterialResponse update(Long id, MaterialRequest request) {
        Material entity = requireActiveMaterial(id);
        validateUpdate(entity, request);
        MaterialSnapshot before = MaterialSnapshot.of(entity);
        apply(entity, request);
        applyStatus(entity, request);
        Material saved = saveMaterial(entity);
        MaterialResponse response = toResponse(saved);
        operationLogger.updated(entity, id);
        // 快照冻结：主数据改名不追溯改写历史单据的名称快照。
        materialHistoryRecorder.record(saved.getId(), MaterialHistoryRecorder.SOURCE_MANUAL,
                MaterialHistoryRecorder.TYPE_UPDATED, before, MaterialSnapshot.of(saved), null, null);
        materialDictionaryCache.evictAll();
        return response;
    }

    /**
     * 商品启停：正常 ↔ 禁用，双向可切换。
     *
     * <p>禁用只改变「选商品」查询的可见性，不删除数据、不动历史单据快照；
     * 禁用后的商品仍然可以 detail / 编辑 / 再次启用，引用与删除校验逻辑不变。</p>
     */
    @Transactional
    public MaterialResponse updateStatus(Long id, String status) {
        Material entity = requireActiveMaterial(id);
        // 空值沿用 STATUS_GUARD 的统一文案「状态不能为空」，再校验取值只允许 正常/禁用。
        String nextStatus = StatusConstants.normalizeActiveStatus(
                STATUS_GUARD.normalizeRequiredStatus(status), "商品状态");
        String currentStatus = currentStatus(entity);
        if (currentStatus.equals(nextStatus)) {
            return toResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(ACTIVE_STATUS_TRANSITIONS, currentStatus, nextStatus);
        entity.setStatus(nextStatus);
        Material saved = saveMaterial(entity);
        operationLogger.updated(saved, id);
        materialDictionaryCache.evictAll();
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Material entity = requireActiveMaterial(id);
        materialReferenceGuard.assertNoReferences(entity);
        MaterialSnapshot before = MaterialSnapshot.of(entity);
        entity.setDeletedFlag(true);
        saveMaterial(entity);
        operationLogger.deleted(entity, id);
        materialDictionaryCache.evictAll();
        materialHistoryRecorder.record(entity.getId(), MaterialHistoryRecorder.SOURCE_MANUAL,
                MaterialHistoryRecorder.TYPE_DELETED, before, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<MaterialResponse> page(PageQuery query, String keyword, String category, String material,
                                       String materialType, String status) {
        Pageable pageable = query.sortBy() != null
                ? query.toPageable("id")
                : PageRequest.of(query.page(), query.size(), MaterialSearchPolicy.DEFAULT_SORT);
        return materialRepository.findAll(
                MaterialSearchPolicy.page(keyword, category, material, materialType,
                        StatusConstants.normalizeOptionalActiveStatus(status, "商品状态")), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS,
            key = "'" + MaterialDictionaryCache.GRADES_CACHE_KEY + "'",
            unless = "#result == null || #result.isEmpty()")
    public List<String> materialGrades() {
        return materialRepository.findDistinctMaterials();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS,
            key = "'" + MaterialDictionaryCache.BRANDS_CACHE_KEY + "'",
            unless = "#result == null || #result.isEmpty()")
    public List<String> materialBrands() {
        return materialRepository.findDistinctActiveProductBrands();
    }

    private Material requireActiveMaterial(Long id) {
        return materialRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));
    }

    /** 当前状态：迁移前的历史行若为空按「正常」处理；异常取值交给统一校验报错。 */
    private String currentStatus(Material entity) {
        String status = StatusConstants.normalizeOptionalActiveStatus(entity.getStatus(), "商品状态");
        return status == null ? StatusConstants.NORMAL : status;
    }

    private void validateCreate(MaterialRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.materialCode());
        validateCreateStatus(request.status());
        identityService.ensureUnique(null, identity(request));
    }

    /**
     * 新建商品的状态只能是「正常」：显式传其它值直接拒绝（422），
     * 不静默忽略，避免调用方以为「创建即禁用」已生效。禁用既有商品请走启停接口。
     */
    private void validateCreateStatus(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        if (!StatusConstants.NORMAL.equals(StatusConstants.normalizeActiveStatus(status, "商品状态"))) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "新建商品的状态只能是「" + StatusConstants.NORMAL + "」，禁用请使用启停接口"
            );
        }
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
        entity.setMaterial(requireText(request.material(), "名称不能为空"));
        entity.setCategory(materialCategoryGuard.requireActive(request.category()));
        entity.setSpec(requireText(request.spec(), "规格不能为空"));
        entity.setLength(trimToNull(request.length()));
        entity.setUnit(requireText(request.unit(), "单位不能为空"));
        entity.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(trimToNull(request.quantityUnit())));
        entity.setPieceWeightTon(requireWeight(request.pieceWeightTon()));
        entity.setPiecesPerBundle(request.piecesPerBundle() == null ? 0 : request.piecesPerBundle());
        entity.setUnitPrice(request.unitPrice() == null ? BigDecimal.ZERO : request.unitPrice());
        entity.setRemark(trimToNull(request.remark()));
    }

    /**
     * 更新时允许「正常 ↔ 禁用」双向切换：status 为 null 表示保持原值，
     * 显式传值则按 StatusConstants 校验（空串/非法值 422）。
     */
    private void applyStatus(Material entity, MaterialRequest request) {
        if (request.status() == null) {
            return;
        }
        entity.setStatus(StatusConstants.normalizeActiveStatus(request.status(), "商品状态"));
    }

    /** 附加费用类：物理属性列存空串/零值，名称即品名，类别固定"附加费用"。 */
    private void applyExpense(Material entity, MaterialRequest request) {
        entity.setMaterialType(MaterialRequest.TYPE_EXPENSE);
        entity.setBrand("");
        entity.setMaterial(requireText(request.material(), "名称不能为空"));
        entity.setCategory(MaterialTypes.EXPENSE_CATEGORY);
        entity.setSpec("");
        entity.setLength("");
        String unit = requireText(request.unit(), "单位不能为空");
        entity.setUnit(unit);
        entity.setQuantityUnit(unit);
        entity.setPieceWeightTon(BigDecimal.ZERO);
        entity.setPiecesPerBundle(0);
        entity.setUnitPrice(request.unitPrice() == null ? BigDecimal.ZERO : request.unitPrice());
        entity.setRemark(trimToNull(request.remark()));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
