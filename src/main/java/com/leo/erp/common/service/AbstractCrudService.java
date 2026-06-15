package com.leo.erp.common.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.support.SecurityPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractCrudService<E extends AbstractAuditableEntity, Req, Res> {
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();

    private final SnowflakeIdGenerator idGenerator;
    private CrudRuntimeSettings crudRuntimeSettings;
    private BusinessNumberAllocator businessNumberAllocator;
    private BusinessPreallocationService businessPreallocationService;
    private final CrudStatusGuard statusGuard = new CrudStatusGuard();

    protected AbstractCrudService(SnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Autowired(required = false)
    protected void setCrudRuntimeSettings(CrudRuntimeSettings crudRuntimeSettings) {
        this.crudRuntimeSettings = crudRuntimeSettings;
    }

    @Autowired(required = false)
    protected void setBusinessNumberAllocator(BusinessNumberAllocator businessNumberAllocator) {
        this.businessNumberAllocator = businessNumberAllocator;
    }

    @Autowired(required = false)
    protected void setBusinessPreallocationService(BusinessPreallocationService businessPreallocationService) {
        this.businessPreallocationService = businessPreallocationService;
    }

    private SnowflakeIdGenerator idGen() {
        return idGenerator != null ? idGenerator : SnowflakeIdGenerator.getInstance();
    }

    private Logger logger() {
        return LoggerFactory.getLogger(getClass());
    }

    private BusinessCreateIdResolver createIdResolver() {
        return new BusinessCreateIdResolver(idGenerator, businessPreallocationService, getClass());
    }

    @Transactional(readOnly = true)
    public Res detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public Res create(Req request) {
        E entity = newEntity();
        BusinessCreateIdResolver createIdResolver = createIdResolver();
        CreateEntityId createEntityId = createIdResolver.resolve();
        assignId(entity, createEntityId.id());
        request = normalizeCreateRequest(request, createEntityId.id());
        validateCreate(request);
        apply(entity, request);
        statusGuard.assertRequestDidNotWriteFinalStatus(entity);
        Res response = toSavedResponse(saveEntity(entity));
        createIdResolver.consumeAfterCommit(createEntityId);
        logger().info("{} created: id={}", entity.getClass().getSimpleName(), createEntityId.id());
        return response;
    }

    @Transactional
    public Res update(Long id, Req request) {
        E entity = requireEntity(id);
        request = normalizeUpdateRequest(entity, request);
        assertEditAllowedByStatus(entity, request);
        validateUpdate(entity, request);
        Optional<String> currentStatus = statusGuard.resolveStatus(entity);
        apply(entity, request);
        statusGuard.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        statusGuard.assertRequestDidNotWriteFinalStatus(entity);
        Res response = toSavedResponse(saveUpdatedEntity(entity, request));
        logger().info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    @Transactional
    public Res updateStatus(Long id, String status) {
        E entity = requireEntity(id);
        String currentStatus = statusGuard.resolveStatus(entity).orElse("");
        String nextStatus = statusGuard.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toSavedResponse(entity);
        }
        statusGuard.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        statusGuard.writeStatus(entity, nextStatus);
        Res response = toSavedResponse(saveStatusEntity(entity));
        logger().info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
        return response;
    }

    @Transactional
    public void delete(Long id) {
        E entity = requireEntity(id);
        assertDeleteAllowedByStatus(entity);
        beforeDelete(entity);
        entity.setDeletedFlag(true);
        statusGuard.markDeletedStatus(entity, allowAdminViewDeletedRecords());
        saveEntity(entity);
        logger().info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    protected final Page<Res> page(PageQuery query, Specification<E> specification, JpaSpecificationExecutor<E> repository) {
        return pageEntities(query, specification, repository)
                .map(this::toResponse);
    }

    protected boolean shouldApplyDataScope() {
        return true;
    }

    private Specification<E> withDataScope(Specification<E> spec) {
        return shouldApplyDataScope() ? DataScopeContext.apply(spec) : spec;
    }

    protected final Page<E> pageEntities(PageQuery query, Specification<E> specification, JpaSpecificationExecutor<E> repository) {
        Specification<E> effectiveSpec = applyListVisibilityPolicy(applyDeletedVisibilityPolicy(specification));
        return repository.findAll(withDataScope(effectiveSpec), query.toPageable("id"));
    }

    protected final List<Res> search(String keyword, String[] searchFields, int maxSize,
                                      Specification<E> baseSpec, JpaSpecificationExecutor<E> repository) {
        Specification<E> spec = combineSpecifications(
                applyListVisibilityPolicy(applyDeletedVisibilityPolicy(baseSpec)),
                com.leo.erp.common.persistence.Specs.keywordLike(keyword, searchFields)
        );
        return repository.findAll(withDataScope(spec), org.springframework.data.domain.PageRequest.of(0, maxSize))
                .map(this::toResponse)
                .toList();
    }

    protected final E requireEntity(Long id) {
        E entity = findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        DataScopeContext.assertCanAccess(entity);
        return entity;
    }

    protected final E requireDetailEntity(Long id) {
        E entity = resolveDetailEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        DataScopeContext.assertCanAccess(entity);
        return entity;
    }

    protected final long nextId() {
        return idGen().nextId();
    }

    protected void validateCreate(Req request) {
    }

    protected void validateUpdate(E entity, Req request) {
    }

    protected Req normalizeCreateRequest(Req request) {
        return request;
    }

    protected Req normalizeCreateRequest(Req request, long entityId) {
        return normalizeCreateRequest(request);
    }

    protected Req normalizeUpdateRequest(E entity, Req request) {
        return request;
    }

    protected void beforeDelete(E entity) {
    }

    protected E saveStatusEntity(E entity) {
        return saveEntity(entity);
    }

    protected E saveUpdatedEntity(E entity, Req request) {
        return saveEntity(entity);
    }

    protected boolean allowProtectedStatusUpdate(E entity, Req request) {
        return false;
    }

    protected Set<String> allowedStatusTransitions() {
        return Set.of();
    }

    protected void beforeStatusUpdate(E entity, String currentStatus, String nextStatus) {
    }

    protected final String nextBusinessNo(String moduleKey) {
        if (businessNumberAllocator == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "编号规则服务不可用");
        }
        String generatedNo = businessNumberAllocator.nextValueByModuleKey(moduleKey);
        if (generatedNo == null || generatedNo.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "模块未配置编号规则: " + moduleKey);
        }
        return generatedNo;
    }

    protected final String resolveCreateBusinessNo(String moduleKey, String requestedNo) {
        return resolveCreateBusinessNo(moduleKey, requestedNo, null);
    }

    protected final String resolveCreateBusinessNo(String moduleKey, String requestedNo, Long entityId) {
        if (crudRuntimeSettings != null && crudRuntimeSettings.shouldUseSnowflakeIdAsBusinessNo()) {
            if (entityId == null || entityId <= 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
            }
            return String.valueOf(entityId);
        }
        if (businessNumberAllocator == null) {
            if (requestedNo == null || requestedNo.isBlank()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "编号规则服务不可用");
            }
            return requestedNo;
        }
        if (isReservedBusinessNo(moduleKey, requestedNo)) {
            consumeReservedBusinessNoAfterCommit(moduleKey, requestedNo.trim());
            return requestedNo.trim();
        }
        return nextBusinessNo(moduleKey);
    }

    protected boolean allowAdminViewDeletedRecords() {
        return false;
    }

    protected Optional<E> findVisibleEntity(Long id) {
        return findActiveEntity(id);
    }

    private void assertEditAllowedByStatus(E entity, Req request) {
        statusGuard.assertEditAllowed(entity, allowProtectedStatusUpdate(entity, request));
    }

    private void assertDeleteAllowedByStatus(E entity) {
        statusGuard.assertDeleteAllowed(entity);
    }

    private Specification<E> applyListVisibilityPolicy(Specification<E> specification) {
        return VISIBILITY_POLICY.applyListVisibility(specification, crudRuntimeSettings);
    }

    protected final Specification<E> applyDeletedVisibilityPolicy(Specification<E> specification) {
        return VISIBILITY_POLICY.applyDeletedVisibility(specification, shouldAdminViewDeletedRecords());
    }

    private Optional<E> resolveDetailEntity(Long id) {
        if (shouldAdminViewDeletedRecords()) {
            return findVisibleEntity(id);
        }
        return findActiveEntity(id);
    }

    private boolean shouldAdminViewDeletedRecords() {
        return allowAdminViewDeletedRecords()
                && crudRuntimeSettings != null
                && crudRuntimeSettings.shouldAdminSeeDeletedRecords()
                && currentUserIsAdmin();
    }

    private boolean currentUserIsAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private Optional<SecurityPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    private boolean isReservedBusinessNo(String moduleKey, String requestedNo) {
        if (businessPreallocationService == null || requestedNo == null || requestedNo.isBlank()) {
            return false;
        }
        return currentPrincipal()
                .map(principal -> businessPreallocationService.isBusinessNoReservedByPrincipal(
                        moduleKey,
                        requestedNo.trim(),
                        principal
                ))
                .orElse(false);
    }

    private void consumeReservedBusinessNoAfterCommit(String moduleKey, String businessNo) {
        if (businessPreallocationService == null || businessNo == null || businessNo.isBlank()) {
            return;
        }
        Runnable consume = () -> businessPreallocationService.consumeBusinessNo(moduleKey, businessNo);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            consume.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                consume.run();
            }
        });
    }

    private Specification<E> combineSpecifications(Specification<E> left, Specification<E> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.and(right);
    }

    protected Res toDetailResponse(E entity) {
        return toResponse(entity);
    }

    protected Res toSavedResponse(E entity) {
        return toResponse(entity);
    }

    protected abstract E newEntity();

    protected abstract void assignId(E entity, Long id);

    protected abstract Optional<E> findActiveEntity(Long id);

    protected abstract String notFoundMessage();

    protected abstract void apply(E entity, Req request);

    protected abstract E saveEntity(E entity);

    protected abstract Res toResponse(E entity);
}
