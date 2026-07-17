package com.leo.erp.common.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.RbacAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractCrudService<E extends AbstractAuditableEntity, Req, Res> {
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();

    private final SnowflakeIdGenerator idGenerator;
    private CrudRuntimeSettings crudRuntimeSettings;
    private RbacAuthorizationService rbacAuthorizationService;
    private final CrudStatusGuard statusGuard = new CrudStatusGuard();

    protected AbstractCrudService(SnowflakeIdGenerator idGenerator) {
        if (idGenerator == null) {
            throw new IllegalArgumentException("SnowflakeIdGenerator must not be null");
        }
        this.idGenerator = idGenerator;
    }

    @Autowired(required = false)
    protected void setCrudRuntimeSettings(CrudRuntimeSettings crudRuntimeSettings) {
        this.crudRuntimeSettings = crudRuntimeSettings;
    }

    @Autowired(required = false)
    protected void setRbacAuthorizationService(RbacAuthorizationService rbacAuthorizationService) {
        this.rbacAuthorizationService = rbacAuthorizationService;
    }

    private Logger logger() {
        return LoggerFactory.getLogger(getClass());
    }

    @Transactional(readOnly = true)
    public Res detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public Res create(Req request) {
        E entity = newEntity();
        long entityId = idGenerator.nextId();
        assignId(entity, entityId);
        request = normalizeCreateRequest(request, entityId);
        validateCreate(request);
        apply(entity, request);
        statusGuard.assertRequestDidNotWriteFinalStatus(entity);
        Res response = toSavedResponse(saveCreatedEntity(entity, request));
        logger().info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
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
        if (!allowRequestToWriteFinalStatus(entity, request, currentStatus)) {
            statusGuard.assertRequestDidNotWriteFinalStatus(entity);
        }
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

    protected boolean allowRequestToWriteFinalStatus(E entity,
                                                     Req request,
                                                     Optional<String> currentStatus) {
        return false;
    }

    @Transactional
    public void delete(Long id) {
        E entity = requireEntity(id);
        assertDeleteAllowedByStatus(entity);
        beforeDelete(entity);
        entity.setDeletedFlag(true);
        saveEntity(entity);
        afterDelete(entity);
        logger().info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    protected final Page<Res> page(PageQuery query, Specification<E> specification, JpaSpecificationExecutor<E> repository) {
        return pageEntities(query, specification, repository)
                .map(this::toResponse);
    }

    protected final Page<E> pageEntities(PageQuery query, Specification<E> specification, JpaSpecificationExecutor<E> repository) {
        Specification<E> effectiveSpec = applyListVisibilityPolicy(applyDeletedVisibilityPolicy(specification));
        return repository.findAll(effectiveSpec, query.toPageable("id"));
    }

    protected final List<Res> search(String keyword, String[] searchFields, int maxSize,
                                      Specification<E> baseSpec, JpaSpecificationExecutor<E> repository) {
        Specification<E> spec = combineSpecifications(
                applyListVisibilityPolicy(applyDeletedVisibilityPolicy(baseSpec)),
                com.leo.erp.common.persistence.Specs.keywordLike(keyword, searchFields)
        );
        return repository.findAll(spec, org.springframework.data.domain.PageRequest.of(0, maxSize))
                .map(this::toResponse)
                .toList();
    }

    protected final E requireEntity(Long id) {
        return findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    protected final E requireDetailEntity(Long id) {
        return resolveDetailEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    protected final long nextId() {
        return idGenerator.nextId();
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

    protected void afterDelete(E entity) {
    }

    protected E saveStatusEntity(E entity) {
        return saveEntity(entity);
    }

    protected E saveCreatedEntity(E entity, Req request) {
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

    protected final String resolveCreateBusinessNo(Long entityId) {
        if (entityId == null || entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
        }
        return String.valueOf(entityId);
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
                && rbacAuthorizationService != null
                && rbacAuthorizationService.check("document", "view_deleted");
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
