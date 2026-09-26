package com.leo.erp.master.material.service;

import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.mapper.MaterialCategoryMapper;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryRequest;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class MaterialCategoryService {

    private static final String CODE_MODULE_KEY = "material-categories";
    private static final String MATERIAL_CATEGORY_OPTIONS_CACHE_KEY = "leo:material-category:all";
    private static final CrudStatusGuard<MaterialCategory> STATUS_GUARD = CrudStatusGuard.withoutStatus();
    private static final Set<StatusTransition> NO_STATUS_TRANSITIONS = Set.of();

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(MaterialCategoryService.class);
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final MaterialCategoryRepository repository;
    private final MaterialCategoryMapper materialCategoryMapper;
    private final MasterDataCodeIssuanceService codeIssuanceService;

    public MaterialCategoryService(SnowflakeIdGenerator idGenerator,
                                   MaterialCategoryRepository repository,
                                   MaterialCategoryMapper materialCategoryMapper,
                                   MasterDataCodeIssuanceService codeIssuanceService) {
        this.snowflakeIdGenerator = idGenerator;
        this.repository = repository;
        this.materialCategoryMapper = materialCategoryMapper;
        this.codeIssuanceService = codeIssuanceService;
    }

    @Transactional(readOnly = true)
    public MaterialCategoryResponse detail(Long id) {
        return toResponse(requireActiveCategory(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + MATERIAL_CATEGORY_OPTIONS_CACHE_KEY + "'")
    public MaterialCategoryResponse create(MaterialCategoryRequest request) {
        MaterialCategory entity = new MaterialCategory();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        validateCreate(request);
        apply(entity, request);
        MaterialCategory saved = saveCreatedCategory(entity);
        operationLogger.created(entity, entityId);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + MATERIAL_CATEGORY_OPTIONS_CACHE_KEY + "'")
    public MaterialCategoryResponse update(Long id, MaterialCategoryRequest request) {
        MaterialCategory entity = requireActiveCategory(id);
        apply(entity, request);
        MaterialCategory saved = repository.save(entity);
        operationLogger.updated(entity, id);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + MATERIAL_CATEGORY_OPTIONS_CACHE_KEY + "'")
    public MaterialCategoryResponse updateStatus(Long id, String status) {
        MaterialCategory entity = requireActiveCategory(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(NO_STATUS_TRANSITIONS, currentStatus, nextStatus);
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.STATUS_CHANGE_UNSUPPORTED);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + MATERIAL_CATEGORY_OPTIONS_CACHE_KEY + "'")
    public void delete(Long id) {
        MaterialCategory entity = requireActiveCategory(id);
        entity.setDeletedFlag(true);
        repository.save(entity);
        operationLogger.deleted(entity, id);
    }

    @Transactional(readOnly = true)
    public Page<MaterialCategoryResponse> page(PageQuery query, String keyword, String status) {
        Specification<MaterialCategory> spec = Specs.<MaterialCategory>notDeleted()
                .and(Specs.keywordLike(keyword, "categoryCode", "categoryName"))
                .and(Specs.equalIfPresent("status", status));
        return repository.findAll(spec, query.toPageable("sortOrder"))
                .map(this::toResponse);
    }

    public MaterialCategoryResponse toResponse(MaterialCategory entity) {
        return materialCategoryMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'" + MATERIAL_CATEGORY_OPTIONS_CACHE_KEY + "'",
            unless = "#result == null || #result.isEmpty()")
    public List<MaterialCategoryOptionResponse> options() {
        return repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc(StatusConstants.NORMAL)
                .stream()
                .map(materialCategoryMapper::toOptionResponse)
                .toList();
    }

    private MaterialCategory requireActiveCategory(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品类别不存在"));
    }

    private void validateCreate(MaterialCategoryRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.categoryCode());
    }

    private void apply(MaterialCategory entity, MaterialCategoryRequest request) {
        entity.setCategoryCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getCategoryCode(),
                request.categoryCode()
        ));
        entity.setCategoryName(required(request.categoryName(), "类别名称"));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setPurchaseWeighRequired(Boolean.TRUE.equals(request.purchaseWeighRequired()));
        entity.setStatus(request.status() == null || request.status().isBlank()
                ? StatusConstants.NORMAL
                : request.status().trim());
        entity.setRemark(optional(request.remark()));
    }

    private MaterialCategory saveCreatedCategory(MaterialCategory entity) {
        MaterialCategory saved = repository.save(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getCategoryCode());
        return saved;
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "不能为空");
        }
        return value.trim();
    }

    private String optional(String value) {
        return value == null ? null : value.trim().isEmpty() ? null : value.trim();
    }

}
