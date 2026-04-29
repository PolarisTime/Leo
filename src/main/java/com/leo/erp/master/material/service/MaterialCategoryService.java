package com.leo.erp.master.material.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.master.material.web.dto.MaterialCategoryRequest;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MaterialCategoryService extends AbstractCrudService<MaterialCategory, MaterialCategoryRequest, MaterialCategoryResponse> {

    private final MaterialCategoryRepository repository;

    public MaterialCategoryService(MaterialCategoryRepository repository, SnowflakeIdGenerator idGenerator) {
        super(idGenerator);
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<MaterialCategoryResponse> page(PageQuery query, String keyword, String status) {
        Specification<MaterialCategory> spec = Specs.<MaterialCategory>notDeleted()
                .and(Specs.keywordLike(keyword, "categoryCode", "categoryName"))
                .and(Specs.equalIfPresent("status", status));
        return repository.findAll(spec, query.toPageable("sortOrder"))
                .map(this::toResponse);
    }

    @Override
    public MaterialCategoryResponse toResponse(MaterialCategory entity) {
        return new MaterialCategoryResponse(
                entity.getId(),
                entity.getCategoryCode(),
                entity.getCategoryName(),
                entity.getSortOrder(),
                Boolean.TRUE.equals(entity.getPurchaseWeighRequired()),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    @Override
    protected void validateCreate(MaterialCategoryRequest request) {
        String code = normalizeCode(request.categoryCode());
        if (repository.findByCategoryCodeAndDeletedFlagFalse(code).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "类别编码已存在");
        }
    }

    @Override
    protected void validateUpdate(MaterialCategory entity, MaterialCategoryRequest request) {
        String code = normalizeCode(request.categoryCode());
        if (!entity.getCategoryCode().equals(code)
                && repository.findByCategoryCodeAndDeletedFlagFalse(code).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "类别编码已存在");
        }
    }

    @Override
    protected MaterialCategory newEntity() {
        return new MaterialCategory();
    }

    @Override
    protected void assignId(MaterialCategory entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<MaterialCategory> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "商品类别不存在";
    }

    @Override
    protected void apply(MaterialCategory entity, MaterialCategoryRequest request) {
        entity.setCategoryCode(normalizeCode(request.categoryCode()));
        entity.setCategoryName(required(request.categoryName(), "类别名称"));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setPurchaseWeighRequired(Boolean.TRUE.equals(request.purchaseWeighRequired()));
        entity.setStatus(request.status() == null || request.status().isBlank() ? "正常" : request.status().trim());
        entity.setRemark(optional(request.remark()));
    }

    @Override
    protected MaterialCategory saveEntity(MaterialCategory entity) {
        return repository.save(entity);
    }

    private String normalizeCode(String value) {
        String v = required(value, "类别编码");
        return v.length() > 32 ? v.substring(0, 32) : v;
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
