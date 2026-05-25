package com.leo.erp.security.permission;

import lombok.extern.slf4j.Slf4j;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
class DepartmentScopeResolver {

    private static final Duration DEPARTMENT_CACHE_TTL = Duration.ofMinutes(5);

    private final ConcurrentHashMap<Long, DepartmentCacheEntry> departmentUserCache = new ConcurrentHashMap<>();
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;

    DepartmentScopeResolver(Optional<UserAccountRepository> userAccountRepository,
                            Optional<DepartmentRepository> departmentRepository) {
        this.userAccountRepository = userAccountRepository == null ? null : userAccountRepository.orElse(null);
        this.departmentRepository = departmentRepository == null ? null : departmentRepository.orElse(null);
    }

    Set<Long> getOwnerUserIds(Long userId, String scope) {
        String normalizedScope = ResourcePermissionCatalog.normalizeDataScope(scope);
        if (userId == null || ResourcePermissionCatalog.SCOPE_ALL.equals(normalizedScope)) {
            return null;
        }
        if (ResourcePermissionCatalog.SCOPE_DEPARTMENT.equals(normalizedScope)) {
            return getDepartmentUserIds(userId);
        }
        return Set.of(userId);
    }

    void evictDepartmentUserCache(Long departmentId) {
        if (departmentId != null) {
            departmentUserCache.remove(departmentId);
        }
    }

    void clearDepartmentUserCache() {
        departmentUserCache.clear();
    }

    private Set<Long> getDepartmentUserIds(Long userId) {
        if (userAccountRepository == null) {
            log.warn("UserAccountRepository 未注入，部门数据范围降级为本人数据范围，userId={}", userId);
            return Set.of(userId);
        }
        Optional<UserAccount> currentUser = userAccountRepository.findByIdAndDeletedFlagFalse(userId);
        Long departmentId = currentUser.map(UserAccount::getDepartmentId).orElse(null);
        if (departmentId == null) {
            log.debug("用户未分配部门，数据范围降级为本人数据范围，userId={}", userId);
            return Set.of(userId);
        }
        if (!isActiveDepartment(departmentId)) {
            departmentUserCache.remove(departmentId);
            log.debug("用户所属部门不可用，数据范围降级为本人数据范围，userId={}, departmentId={}", userId, departmentId);
            return Set.of(userId);
        }

        long now = System.currentTimeMillis();
        DepartmentCacheEntry entry = departmentUserCache.get(departmentId);
        if (entry != null && now < entry.expiresAt()) {
            Set<Long> cached = entry.userIds();
            if (cached.contains(userId)) {
                return cached;
            }
        }

        Set<Long> allDepartmentIds = collectDepartmentAndDescendants(departmentId);
        Set<Long> userIds = new LinkedHashSet<>();
        for (Long deptId : allDepartmentIds) {
            userAccountRepository.findByDepartmentIdAndDeletedFlagFalse(deptId)
                    .stream()
                    .map(UserAccount::getId)
                    .filter(java.util.Objects::nonNull)
                    .forEach(userIds::add);
        }
        userIds.add(userId);
        departmentUserCache.put(departmentId, new DepartmentCacheEntry(
                Set.copyOf(userIds),
                now + DEPARTMENT_CACHE_TTL.toMillis()
        ));
        return userIds;
    }

    private boolean isActiveDepartment(Long departmentId) {
        if (departmentRepository == null) {
            return true;
        }
        return departmentRepository.findByIdAndDeletedFlagFalse(departmentId)
                .map(Department::getStatus)
                .filter(StatusConstants.NORMAL::equals)
                .isPresent();
    }

    private Set<Long> collectDepartmentAndDescendants(Long rootDepartmentId) {
        Set<Long> result = new LinkedHashSet<>();
        result.add(rootDepartmentId);
        if (departmentRepository == null) {
            return result;
        }
        List<Department> allActive = departmentRepository
                .findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc(StatusConstants.NORMAL);
        Map<Long, List<Department>> childrenByParent = allActive.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(Department::getParentId));

        var queue = new ArrayDeque<Long>();
        queue.add(rootDepartmentId);
        Set<Long> visited = new LinkedHashSet<>();
        visited.add(rootDepartmentId);
        while (!queue.isEmpty()) {
            Long parentId = queue.poll();
            for (Department child : childrenByParent.getOrDefault(parentId, List.of())) {
                if (visited.add(child.getId())) {
                    result.add(child.getId());
                    queue.add(child.getId());
                }
            }
        }
        return result;
    }

    private record DepartmentCacheEntry(Set<Long> userIds, long expiresAt) {
    }
}
