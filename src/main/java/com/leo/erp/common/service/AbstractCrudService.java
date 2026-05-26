package com.leo.erp.common.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.norule.service.NoRuleSequenceService;
import com.leo.erp.system.norule.service.PreallocatedBusinessNoService;
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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractCrudService<E extends AbstractAuditableEntity, Req, Res> {
    private static final String PREALLOCATED_ID_HEADER = "X-Preallocated-Id";

    private static final Set<String> PROTECTED_EDIT_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.COMPLETED,
            StatusConstants.PURCHASE_COMPLETED,
            StatusConstants.INBOUND_COMPLETED,
            StatusConstants.SALES_COMPLETED,
            StatusConstants.PAID,
            StatusConstants.RECEIVED,
            StatusConstants.SIGNED,
            StatusConstants.DELIVERED
    );

    private static final Set<String> PROTECTED_DELETE_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.COMPLETED,
            StatusConstants.PURCHASE_COMPLETED,
            StatusConstants.INBOUND_COMPLETED,
            StatusConstants.SALES_COMPLETED,
            StatusConstants.PAID,
            StatusConstants.RECEIVED,
            StatusConstants.SIGNED,
            StatusConstants.DELIVERED
    );

    private final SnowflakeIdGenerator idGenerator;
    private SystemSwitchService systemSwitchService;
    private NoRuleSequenceService noRuleSequenceService;
    private PreallocatedBusinessNoService preallocatedBusinessNoService;

    protected AbstractCrudService(SnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Autowired(required = false)
    protected void setSystemSwitchService(SystemSwitchService systemSwitchService) {
        this.systemSwitchService = systemSwitchService;
    }

    @Autowired(required = false)
    protected void setNoRuleSequenceService(NoRuleSequenceService noRuleSequenceService) {
        this.noRuleSequenceService = noRuleSequenceService;
    }

    @Autowired(required = false)
    protected void setPreallocatedBusinessNoService(PreallocatedBusinessNoService preallocatedBusinessNoService) {
        this.preallocatedBusinessNoService = preallocatedBusinessNoService;
    }

    private SnowflakeIdGenerator idGen() {
        return idGenerator != null ? idGenerator : SnowflakeIdGenerator.getInstance();
    }

    private Logger logger() {
        return LoggerFactory.getLogger(getClass());
    }

    private long resolveCreateEntityId() {
        Long preallocatedId = resolvePreallocatedIdFromRequest();
        return preallocatedId != null ? preallocatedId : idGen().nextId();
    }

    private Long resolvePreallocatedIdFromRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        String rawValue = servletRequestAttributes.getRequest().getHeader(PREALLOCATED_ID_HEADER);
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            long parsedValue = Long.parseLong(rawValue.trim());
            if (parsedValue > 0 && preallocatedBusinessNoService != null) {
                preallocatedBusinessNoService.consumeOrThrow(resolveBusinessModuleKey(), parsedValue, currentPrincipal());
            }
            return parsedValue > 0 ? parsedValue : null;
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "预分配雪花ID格式不正确");
        }
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return principal;
    }

    private String resolveBusinessModuleKey() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无法识别当前请求模块");
        }
        String uri = servletRequestAttributes.getRequest().getRequestURI();
        if (uri == null || uri.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无法识别当前请求模块");
        }
        String path = uri.startsWith("/api/") ? uri.substring(5) : uri;
        int slashIndex = path.indexOf('/');
        return slashIndex >= 0 ? path.substring(0, slashIndex) : path;
    }

    @Transactional(readOnly = true)
    public final Res detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public final Res create(Req request) {
        E entity = newEntity();
        long id = resolveCreateEntityId();
        assignId(entity, id);
        request = normalizeCreateRequest(request, id);
        validateCreate(request);
        apply(entity, request);
        Res response = toSavedResponse(saveEntity(entity));
        logger().info("{} created: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    @Transactional
    public final Res update(Long id, Req request) {
        E entity = requireEntity(id);
        request = normalizeUpdateRequest(entity, request);
        assertEditAllowedByStatus(entity, request);
        validateUpdate(entity, request);
        apply(entity, request);
        Res response = toSavedResponse(saveEntity(entity));
        logger().info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    @Transactional
    public final void delete(Long id) {
        E entity = requireEntity(id);
        assertDeleteAllowedByStatus(entity);
        beforeDelete(entity);
        entity.setDeletedFlag(Boolean.TRUE);
        markDeletedStatus(entity);
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

    protected boolean allowProtectedStatusUpdate(E entity, Req request) {
        return false;
    }

    protected final String nextBusinessNo(String moduleKey) {
        if (noRuleSequenceService == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "编号规则服务不可用");
        }
        String generatedNo = noRuleSequenceService.nextValueByModuleKey(moduleKey);
        if (generatedNo == null || generatedNo.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "模块未配置编号规则: " + moduleKey);
        }
        return generatedNo;
    }

    protected final String resolveCreateBusinessNo(String moduleKey, String requestedNo) {
        return resolveCreateBusinessNo(moduleKey, requestedNo, null);
    }

    protected final String resolveCreateBusinessNo(String moduleKey, String requestedNo, Long entityId) {
        if (systemSwitchService != null && systemSwitchService.shouldUseSnowflakeIdAsBusinessNo()) {
            if (entityId == null || entityId <= 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
            }
            return String.valueOf(entityId);
        }
        if (noRuleSequenceService == null) {
            if (requestedNo == null || requestedNo.isBlank()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "编号规则服务不可用");
            }
            return requestedNo;
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
        resolveStatus(entity).ifPresent(status -> {
            if (PROTECTED_EDIT_STATUSES.contains(status) && !allowProtectedStatusUpdate(entity, request)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "当前单据状态为「" + status + "」，不能编辑"
                );
            }
        });
    }

    private void assertDeleteAllowedByStatus(E entity) {
        resolveStatus(entity).ifPresent(status -> {
            if (PROTECTED_DELETE_STATUSES.contains(status)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "当前单据状态为「" + status + "」，不能删除"
                );
            }
        });
    }

    private Specification<E> applyListVisibilityPolicy(Specification<E> specification) {
        if (systemSwitchService == null) {
            return specification;
        }
        Set<String> hiddenStatuses = systemSwitchService.getHiddenAuditedStatuses();
        if (hiddenStatuses.isEmpty()) {
            return specification;
        }
        return combineSpecifications(specification, excludeStatuses(hiddenStatuses));
    }

    protected final Specification<E> applyDeletedVisibilityPolicy(Specification<E> specification) {
        if (shouldAdminViewDeletedRecords()) {
            return specification;
        }
        Specification<E> activeOnly = (root, query, criteriaBuilder) ->
                criteriaBuilder.isFalse(root.get("deletedFlag"));
        return specification == null ? activeOnly : specification.and(activeOnly);
    }

    private Specification<E> excludeStatuses(Set<String> hiddenStatuses) {
        return (root, query, criteriaBuilder) -> {
            try {
                root.getModel().getAttribute("status");
            } catch (IllegalArgumentException ex) {
                return criteriaBuilder.conjunction();
            }
            var statusPath = root.get("status");
            return criteriaBuilder.or(
                    criteriaBuilder.isNull(statusPath),
                    criteriaBuilder.not(statusPath.in(hiddenStatuses))
            );
        };
    }

    private Optional<E> resolveDetailEntity(Long id) {
        if (shouldAdminViewDeletedRecords()) {
            return findVisibleEntity(id);
        }
        return findActiveEntity(id);
    }

    private boolean shouldAdminViewDeletedRecords() {
        return allowAdminViewDeletedRecords()
                && systemSwitchService != null
                && systemSwitchService.shouldAdminSeeDeletedRecords()
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

    private Optional<String> resolveStatus(E entity) {
        try {
            Method getter = entity.getClass().getMethod("getStatus");
            Object value = getter.invoke(entity);
            if (value == null) {
                return Optional.empty();
            }
            String status = String.valueOf(value).trim();
            return status.isBlank() ? Optional.empty() : Optional.of(status);
        } catch (NoSuchMethodException ignored) {
            return Optional.empty();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("读取单据状态失败", ex);
        }
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

    private void markDeletedStatus(E entity) {
        if (!allowAdminViewDeletedRecords()) {
            return;
        }
        try {
            Method setter = entity.getClass().getMethod("setStatus", String.class);
            setter.invoke(entity, StatusConstants.DELETED);
        } catch (NoSuchMethodException ignored) {
            // Entities without a main status field do not need deleted status tagging.
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("写入单据删除状态失败", ex);
        }
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
