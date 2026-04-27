package com.leo.erp.common.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AuditableEntity;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public abstract class AbstractCrudService<E extends AuditableEntity, Req, Res> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SnowflakeIdGenerator idGenerator;

    protected AbstractCrudService(SnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Transactional(readOnly = true)
    public final Res detail(Long id) {
        return toDetailResponse(requireEntity(id));
    }

    @Transactional
    public final Res create(Req request) {
        validateCreate(request);
        E entity = newEntity();
        long id = idGenerator.nextId();
        assignId(entity, id);
        apply(entity, request);
        Res response = toSavedResponse(saveEntity(entity));
        logger.info("{} created: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    @Transactional
    public final Res update(Long id, Req request) {
        E entity = requireEntity(id);
        validateUpdate(entity, request);
        apply(entity, request);
        Res response = toSavedResponse(saveEntity(entity));
        logger.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    @Transactional
    public final void delete(Long id) {
        E entity = requireEntity(id);
        beforeDelete(entity);
        entity.setDeletedFlag(Boolean.TRUE);
        saveEntity(entity);
        logger.info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    protected final Page<Res> page(PageQuery query, Specification<E> specification, JpaSpecificationExecutor<E> repository) {
        return repository.findAll(DataScopeContext.apply(specification), query.toPageable("id"))
                .map(this::toResponse);
    }

    /**
     * Lightweight keyword search for dropdowns, selectors, and parent lookups.
     * Returns at most {@code maxSize} rows. Applies data-scope filtering just like
     * {@link #page} — users with SELF/DEPARTMENT scope only see their own data.
     */
    protected final List<Res> search(String keyword, String[] searchFields, int maxSize,
                                      Specification<E> baseSpec, JpaSpecificationExecutor<E> repository) {
        Specification<E> spec = baseSpec
                .and(com.leo.erp.common.persistence.Specs.keywordLike(keyword, searchFields));
        return repository.findAll(DataScopeContext.apply(spec), org.springframework.data.domain.PageRequest.of(0, maxSize))
                .map(this::toResponse)
                .toList();
    }

    protected final E requireEntity(Long id) {
        E entity = findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        DataScopeContext.assertCanAccess(entity);
        return entity;
    }

    protected final long nextId() {
        return idGenerator.nextId();
    }

    protected void validateCreate(Req request) {
    }

    protected void validateUpdate(E entity, Req request) {
    }

    protected void beforeDelete(E entity) {
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
