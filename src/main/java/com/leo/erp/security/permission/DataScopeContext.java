package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AuditableEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DataScopeContext {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    @SuppressWarnings("rawtypes")
    private static volatile DataScopeStrategy strategy = CreatedByScopeStrategy.instance();

    private DataScopeContext() {
    }

    public record Context(Long userId, String resource, String scope, Set<Long> ownerUserIds) {
    }

    /**
     * Override the default {@code createdBy}-based strategy.
     * Call this during application startup if you need custom ownership fields.
     */
    @SuppressWarnings("unchecked")
    public static <E extends AuditableEntity> void setStrategy(DataScopeStrategy<E> customStrategy) {
        strategy = customStrategy;
    }

    public static void set(Long userId, String resource, String scope) {
        set(userId, resource, scope, ownerUserIdsForScope(userId, scope));
    }

    public static void set(Long userId, String resource, String scope, Set<Long> ownerUserIds) {
        CURRENT.set(new Context(
                userId,
                resource,
                ResourcePermissionCatalog.normalizeDataScope(scope),
                normalizeOwnerUserIds(ownerUserIds)
        ));
    }

    public static Context current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    @SuppressWarnings("unchecked")
    public static <E extends AuditableEntity> Specification<E> apply(Specification<E> specification) {
        Context context = current();
        Set<Long> ownerUserIds = allowedOwnerUserIds(context);
        if (ownerUserIds == null) {
            return specification;
        }
        Specification<E> ownerSpecification = ((DataScopeStrategy<E>) strategy).toSpecification(ownerUserIds);
        return specification == null ? ownerSpecification : specification.and(ownerSpecification);
    }

    public static void assertCanAccess(AuditableEntity entity) {
        if (!canAccess(entity)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限");
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean canAccess(AuditableEntity entity) {
        Context context = current();
        Set<Long> ownerUserIds = allowedOwnerUserIds(context);
        if (entity == null || ownerUserIds == null) {
            return true;
        }
        if (ownerUserIds.isEmpty()) {
            return false;
        }
        return ((DataScopeStrategy<AuditableEntity>) strategy).canAccess(entity, ownerUserIds);
    }

    public static Set<Long> allowedOwnerUserIds() {
        return allowedOwnerUserIds(current());
    }

    public static boolean hasOwnerFilter() {
        return allowedOwnerUserIds() != null;
    }

    private static Set<Long> allowedOwnerUserIds(Context context) {
        if (context == null || context.userId() == null) {
            return null;
        }
        String scope = ResourcePermissionCatalog.normalizeDataScope(context.scope());
        if (ResourcePermissionCatalog.SCOPE_ALL.equals(scope)) {
            return null;
        }
        return context.ownerUserIds() == null ? Set.of(context.userId()) : context.ownerUserIds();
    }

    private static Set<Long> ownerUserIdsForScope(Long userId, String scope) {
        String normalizedScope = ResourcePermissionCatalog.normalizeDataScope(scope);
        if (userId == null || ResourcePermissionCatalog.SCOPE_ALL.equals(normalizedScope)) {
            return null;
        }
        if (ResourcePermissionCatalog.SCOPE_SELF.equals(normalizedScope)) {
            return Set.of(userId);
        }
        throw new IllegalArgumentException(
                "无法从 scope 自动解析 ownerUserIds，请使用 set(userId, resource, scope, ownerUserIds) 重载: scope=" + scope);
    }

    private static Set<Long> normalizeOwnerUserIds(Set<Long> ownerUserIds) {
        if (ownerUserIds == null) {
            return null;
        }
        return ownerUserIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }
}
