package com.leo.erp.system.department.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.department.web.dto.DepartmentOptionResponse;
import com.leo.erp.system.department.web.dto.DepartmentRequest;
import com.leo.erp.system.department.web.dto.DepartmentResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DepartmentService extends AbstractCrudService<Department, DepartmentRequest, DepartmentResponse> {

    private static final Set<String> ALLOWED_STATUS = Set.of("正常", "禁用");

    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final PermissionService permissionService;

    public DepartmentService(DepartmentRepository departmentRepository,
                             UserAccountRepository userAccountRepository,
                             PermissionService permissionService,
                             SnowflakeIdGenerator idGenerator) {
        super(idGenerator);
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public Page<DepartmentResponse> page(PageQuery query, String keyword, String status) {
        Specification<Department> spec = Specs.<Department>notDeleted()
                .and(Specs.keywordLike(keyword, "departmentCode", "departmentName", "managerName"))
                .and(Specs.equalIfPresent("status", normalizeStatusFilter(status)));
        Page<Department> entityPage = departmentRepository.findAll(spec, query.toPageable("id"));
        Map<Long, String> parentNameById = parentNameMap(entityPage.getContent());
        List<DepartmentResponse> responses = entityPage.getContent().stream()
                .map(department -> toResponse(department, parentNameById))
                .toList();
        return new PageImpl<>(responses, entityPage.getPageable(), entityPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<DepartmentOptionResponse> options() {
        return departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常")
                .stream()
                .map(department -> new DepartmentOptionResponse(
                        department.getId(),
                        department.getDepartmentCode(),
                        department.getDepartmentName()
                ))
                .toList();
    }

    @Override
    protected void validateCreate(DepartmentRequest request) {
        ensureDepartmentCodeUnique(normalizeRequiredValue(request.departmentCode(), "部门编码"), null);
        validateParent(null, request.parentId());
    }

    @Override
    protected void validateUpdate(Department entity, DepartmentRequest request) {
        ensureDepartmentCodeUnique(normalizeRequiredValue(request.departmentCode(), "部门编码"), entity.getId());
        validateParent(entity.getId(), request.parentId());
    }

    @Override
    protected Department newEntity() {
        return new Department();
    }

    @Override
    protected void assignId(Department entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Department> findActiveEntity(Long id) {
        return departmentRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "部门不存在";
    }

    @Override
    protected void apply(Department entity, DepartmentRequest request) {
        entity.setDepartmentCode(normalizeRequiredValue(request.departmentCode(), "部门编码"));
        entity.setDepartmentName(normalizeRequiredValue(request.departmentName(), "部门名称"));
        entity.setParentId(request.parentId());
        entity.setManagerName(normalizeOptionalValue(request.managerName()));
        entity.setContactPhone(normalizeOptionalValue(request.contactPhone()));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setRemark(normalizeOptionalValue(request.remark()));
    }

    @Override
    protected Department saveEntity(Department entity) {
        try {
            Department saved = departmentRepository.save(entity);
            permissionService.evictDepartmentUserCache(saved.getId());
            syncBoundUserDepartmentName(saved);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            if (isDepartmentCodeConflict(ex)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "部门编码已存在");
            }
            throw ex;
        }
    }

    @Override
    protected void beforeDelete(Department entity) {
        if (departmentRepository.existsByParentIdAndDeletedFlagFalse(entity.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "存在下级部门，不能删除");
        }
        if (userAccountRepository.countByDepartmentIdAndDeletedFlagFalse(entity.getId()) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "部门已绑定用户，不能删除");
        }
    }

    @Override
    protected DepartmentResponse toResponse(Department entity) {
        return toResponse(entity, Map.of());
    }

    @Override
    protected DepartmentResponse toDetailResponse(Department entity) {
        return toResponse(entity, parentNameMap(List.of(entity)));
    }

    private DepartmentResponse toResponse(Department entity, Map<Long, String> parentNameById) {
        return new DepartmentResponse(
                entity.getId(),
                entity.getDepartmentCode(),
                entity.getDepartmentName(),
                entity.getParentId(),
                entity.getParentId() == null ? null : parentNameById.get(entity.getParentId()),
                entity.getManagerName(),
                entity.getContactPhone(),
                entity.getSortOrder(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private Map<Long, String> parentNameMap(List<Department> departments) {
        List<Long> parentIds = departments.stream()
                .map(Department::getParentId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findByIdInAndDeletedFlagFalse(parentIds)
                .stream()
                .collect(Collectors.toMap(
                        Department::getId,
                        Department::getDepartmentName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private void ensureDepartmentCodeUnique(String departmentCode, Long currentId) {
        boolean duplicated = departmentRepository.findByDepartmentCodeAndDeletedFlagFalse(departmentCode)
                .map(existing -> currentId == null || !existing.getId().equals(currentId))
                .orElse(false);
        if (duplicated) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "部门编码已存在");
        }
    }

    private void syncBoundUserDepartmentName(Department department) {
        if (Boolean.TRUE.equals(department.getDeletedFlag())) {
            return;
        }
        List<UserAccount> changedUsers = userAccountRepository.findByDepartmentIdAndDeletedFlagFalse(department.getId())
                .stream()
                .filter(user -> !department.getDepartmentName().equals(user.getDepartmentName()))
                .peek(user -> user.setDepartmentName(department.getDepartmentName()))
                .toList();
        if (!changedUsers.isEmpty()) {
            userAccountRepository.saveAll(changedUsers);
        }
    }

    private boolean isDepartmentCodeConflict(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && (message.contains("department_code") || message.contains("uk_sys_department_code_active"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void validateParent(Long currentId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上级部门不能选择自身");
        }
        Department parent = departmentRepository.findByIdAndDeletedFlagFalse(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "上级部门不存在"));
        Long cursor = parent.getParentId();
        while (cursor != null) {
            if (cursor.equals(currentId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上级部门不能选择下级部门");
            }
            cursor = departmentRepository.findByIdAndDeletedFlagFalse(cursor)
                    .map(Department::getParentId)
                    .orElse(null);
        }
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeRequiredValue(value, "状态");
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门状态不合法");
        }
        return normalized;
    }

    private String normalizeStatusFilter(String value) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门状态不合法");
        }
        return normalized;
    }

    private String normalizeRequiredValue(String value, String fieldName) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
