package com.leo.erp.security.permission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.domain.entity.UserRole;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.auth.web.dto.ResourcePermissionResponse;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuRepository;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    private static final String CACHE_PREFIX = "leo:resource-perm:";
    private static final String SCOPE_CACHE_PREFIX = "leo:resource-scope:";
    private static final long CACHE_TTL_HOURS = 1;
    private static final String MENU_CACHE_KEY = "leo:menu:all";
    private static final String ROLE_STATUS_NORMAL = "正常";
    private static final Duration MENU_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration DEPARTMENT_CACHE_TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<MenuSnapshot>> MENU_LIST_TYPE = new TypeReference<>() { };

    private final ConcurrentHashMap<Long, DepartmentCacheEntry> departmentUserCache = new ConcurrentHashMap<>();

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MenuRepository menuRepository;
    private final StringRedisTemplate redisTemplate;
    private final RoleSettingRepository roleSettingRepository;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public PermissionService(UserRoleRepository userRoleRepository,
                             RolePermissionRepository rolePermissionRepository,
                             MenuRepository menuRepository,
                             StringRedisTemplate redisTemplate,
                             RoleSettingRepository roleSettingRepository,
                             Optional<RedisJsonCacheSupport> redisJsonCacheSupport,
                             Optional<UserAccountRepository> userAccountRepository,
                             Optional<DepartmentRepository> departmentRepository) {
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.menuRepository = menuRepository;
        this.redisTemplate = redisTemplate;
        this.roleSettingRepository = roleSettingRepository;
        this.redisJsonCacheSupport = redisJsonCacheSupport == null ? null : redisJsonCacheSupport.orElse(null);
        this.userAccountRepository = userAccountRepository == null ? null : userAccountRepository.orElse(null);
        this.departmentRepository = departmentRepository == null ? null : departmentRepository.orElse(null);
    }

    public PermissionService(UserRoleRepository userRoleRepository,
                             RolePermissionRepository rolePermissionRepository,
                             MenuRepository menuRepository,
                             StringRedisTemplate redisTemplate,
                             RoleSettingRepository roleSettingRepository,
                             Optional<RedisJsonCacheSupport> redisJsonCacheSupport,
                             Optional<UserAccountRepository> userAccountRepository) {
        this(
                userRoleRepository,
                rolePermissionRepository,
                menuRepository,
                redisTemplate,
                roleSettingRepository,
                redisJsonCacheSupport,
                userAccountRepository,
                Optional.empty()
        );
    }

    public PermissionService(UserRoleRepository userRoleRepository,
                             RolePermissionRepository rolePermissionRepository,
                             MenuRepository menuRepository,
                             StringRedisTemplate redisTemplate,
                             RoleSettingRepository roleSettingRepository,
                             Optional<RedisJsonCacheSupport> redisJsonCacheSupport) {
        this(userRoleRepository, rolePermissionRepository, menuRepository, redisTemplate, roleSettingRepository, redisJsonCacheSupport, Optional.empty());
    }

    public PermissionService(UserRoleRepository userRoleRepository,
                             RolePermissionRepository rolePermissionRepository,
                             MenuRepository menuRepository,
                             StringRedisTemplate redisTemplate,
                             RoleSettingRepository roleSettingRepository) {
        this(userRoleRepository, rolePermissionRepository, menuRepository, redisTemplate, roleSettingRepository, Optional.empty(), Optional.empty());
    }

    public Map<String, Set<String>> getUserPermissionMap(Long userId) {
        return getUserPermissionSnapshot(userId).permissionMap();
    }

    public List<ResourcePermissionResponse> getUserPermissions(Long userId) {
        return getUserPermissionMap(userId).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ResourcePermissionResponse(entry.getKey(), Set.copyOf(entry.getValue())))
                .toList();
    }

    public Map<String, String> getUserDataScopes(Long userId) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : getUserPermissionSnapshot(userId).dataScopeByPermission().entrySet()) {
            String resource = parsePermissionScopeResource(entry.getKey());
            if (resource.isBlank()) {
                continue;
            }
            result.merge(resource, entry.getValue(), ResourcePermissionCatalog::broaderDataScope);
        }
        return result;
    }

    public String getUserDataScope(Long userId, String resourceCode) {
        String resource = ResourcePermissionCatalog.normalizeResource(resourceCode);
        return getUserDataScopes(userId).getOrDefault(resource, ResourcePermissionCatalog.SCOPE_SELF);
    }

    public String getUserDataScope(Long userId, String resourceCode, String actionCode) {
        String resource = ResourcePermissionCatalog.normalizeResource(resourceCode);
        String action = ResourcePermissionCatalog.normalizeAction(actionCode);
        return getUserPermissionSnapshot(userId)
                .dataScopeByPermission()
                .getOrDefault(permissionScopeKey(resource, action), ResourcePermissionCatalog.SCOPE_SELF);
    }

    private UserPermissionSnapshot getUserPermissionSnapshot(Long userId) {
        UserPermissionSnapshot cached = readUserPermissionSnapshotCache(userId);
        if (cached != null) {
            return cached;
        }

        List<RoleSetting> roles = resolveActiveRoles(userId);
        if (roles.isEmpty()) {
            return new UserPermissionSnapshot(Map.of(), Map.of());
        }

        Map<Long, String> dataScopeByRoleId = roles.stream()
                .collect(Collectors.toMap(RoleSetting::getId, role -> ResourcePermissionCatalog.normalizeDataScope(role.getDataScope())));
        List<Long> roleIds = roles.stream().map(RoleSetting::getId).filter(Objects::nonNull).distinct().toList();
        Map<String, Set<String>> permissionMap = new LinkedHashMap<>();
        Map<String, String> dataScopeByPermission = new LinkedHashMap<>();
        for (RolePermission permission : rolePermissionRepository.findByRoleIdInAndDeletedFlagFalse(roleIds)) {
            String resource = ResourcePermissionCatalog.normalizeResource(permission.getResourceCode());
            String action = ResourcePermissionCatalog.normalizeAction(permission.getActionCode());
            if (!ResourcePermissionCatalog.isAllowed(resource, action)) {
                continue;
            }
            String dataScope = dataScopeByRoleId.getOrDefault(permission.getRoleId(), ResourcePermissionCatalog.SCOPE_SELF);
            permissionMap.computeIfAbsent(resource, key -> new LinkedHashSet<>()).add(action);
            dataScopeByPermission.merge(permissionScopeKey(resource, action), dataScope, ResourcePermissionCatalog::broaderDataScope);
        }

        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(permissionMap, dataScopeByPermission);
        writeUserPermissionSnapshotCache(userId, snapshot);
        return snapshot;
    }

    public Set<Long> getDataScopeOwnerUserIds(Long userId, String scope) {
        String normalizedScope = ResourcePermissionCatalog.normalizeDataScope(scope);
        if (userId == null || ResourcePermissionCatalog.SCOPE_ALL.equals(normalizedScope)) {
            return null;
        }
        if (ResourcePermissionCatalog.SCOPE_DEPARTMENT.equals(normalizedScope)) {
            return getDepartmentUserIds(userId);
        }
        return Set.of(userId);
    }

    public Set<String> getVisibleMenuCodes(Long userId) {
        Set<String> menuCodes = new LinkedHashSet<>(ResourcePermissionCatalog.resolveVisibleMenuCodes(getUserPermissionMap(userId)));
        List<Menu> allMenus = getActiveMenus();
        Map<String, String> parentMap = allMenus.stream()
                .filter(menu -> menu.getParentCode() != null)
                .collect(Collectors.toMap(Menu::getMenuCode, Menu::getParentCode, (left, right) -> left));

        Set<String> withParents = new LinkedHashSet<>(menuCodes);
        for (String code : menuCodes) {
            String parent = parentMap.get(code);
            while (parent != null) {
                withParents.add(parent);
                parent = parentMap.get(parent);
            }
        }
        return withParents;
    }

    public boolean can(Long userId, String resourceCode, String actionCode) {
        String resource = ResourcePermissionCatalog.normalizeResource(resourceCode);
        String action = ResourcePermissionCatalog.normalizeAction(actionCode);
        Set<String> actions = getUserPermissionMap(userId).get(resource);
        return actions != null && actions.contains(action);
    }

    public String getPermissionSummaryForRoles(Collection<RoleSetting> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        List<Long> roleIds = roles.stream()
                .map(RoleSetting::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return "";
        }
        return buildPermissionSummary(rolePermissionRepository.findByRoleIdInAndDeletedFlagFalse(roleIds));
    }

    public void evictCache(Long userId) {
        redisTemplate.delete(List.of(CACHE_PREFIX + userId, SCOPE_CACHE_PREFIX + userId));
    }

    public void evictDepartmentUserCache(Long departmentId) {
        if (departmentId != null) {
            departmentUserCache.remove(departmentId);
        }
    }

    public void evictMetadataCache() {
        if (redisJsonCacheSupport == null) {
            return;
        }
        redisJsonCacheSupport.delete(List.of(MENU_CACHE_KEY));
    }

    public void evictAllCache() {
        departmentUserCache.clear();
        evictMetadataCache();
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.deleteByPattern(CACHE_PREFIX + "*");
            redisJsonCacheSupport.deleteByPattern(SCOPE_CACHE_PREFIX + "*");
            return;
        }
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return;
        }
        RedisConnection connection = connectionFactory.getConnection();
        List<String> batch = new ArrayList<>(256);
        try {
            scanAndDelete(connection, CACHE_PREFIX + "*", batch);
            scanAndDelete(connection, SCOPE_CACHE_PREFIX + "*", batch);
        } finally {
            connection.close();
        }
    }

    public List<Menu> getActiveMenus() {
        return loadActiveMenuSnapshots().stream()
                .map(snapshot -> {
                    Menu menu = new Menu();
                    menu.setMenuCode(snapshot.menuCode());
                    menu.setMenuName(snapshot.menuName());
                    menu.setParentCode(snapshot.parentCode());
                    menu.setRoutePath(snapshot.routePath());
                    menu.setIcon(snapshot.icon());
                    menu.setSortOrder(snapshot.sortOrder());
                    menu.setMenuType(snapshot.menuType());
                    menu.setStatus(snapshot.status());
                    return menu;
                })
                .toList();
    }

    private List<Long> resolveRoleIds(Long userId) {
        return resolveActiveRoles(userId).stream()
                .map(RoleSetting::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private UserPermissionSnapshot readUserPermissionSnapshotCache(Long userId) {
        String permissionCacheKey = CACHE_PREFIX + userId;
        String scopeCacheKey = SCOPE_CACHE_PREFIX + userId;
        Map<Object, Object> cachedPermissions = redisTemplate.opsForHash().entries(permissionCacheKey);
        Map<Object, Object> cachedScopes = redisTemplate.opsForHash().entries(scopeCacheKey);
        if (cachedPermissions.isEmpty() || cachedScopes.isEmpty()) {
            return null;
        }
        Map<String, Set<String>> permissionMap = new LinkedHashMap<>();
        cachedPermissions.forEach((key, value) -> permissionMap.put(
                ResourcePermissionCatalog.normalizeResource(key.toString()),
                splitActions(value.toString())
        ));
        Map<String, String> dataScopeByPermission = new LinkedHashMap<>();
        cachedScopes.forEach((key, value) -> {
            String normalizedKey = normalizePermissionScopeKey(key.toString());
            if (!normalizedKey.isBlank()) {
                dataScopeByPermission.put(normalizedKey, ResourcePermissionCatalog.normalizeDataScope(value.toString()));
            }
        });
        return new UserPermissionSnapshot(permissionMap, dataScopeByPermission);
    }

    private void writeUserPermissionSnapshotCache(Long userId, UserPermissionSnapshot snapshot) {
        if (snapshot.permissionMap().isEmpty()) {
            return;
        }
        Map<String, String> permissionCacheData = new LinkedHashMap<>();
        snapshot.permissionMap().forEach((resource, actions) -> permissionCacheData.put(resource, String.join(",", actions)));
        redisTemplate.opsForHash().putAll(CACHE_PREFIX + userId, permissionCacheData);
        redisTemplate.expire(CACHE_PREFIX + userId, CACHE_TTL_HOURS, TimeUnit.HOURS);
        if (!snapshot.dataScopeByPermission().isEmpty()) {
            redisTemplate.opsForHash().putAll(SCOPE_CACHE_PREFIX + userId, snapshot.dataScopeByPermission());
            redisTemplate.expire(SCOPE_CACHE_PREFIX + userId, CACHE_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    private void scanAndDelete(RedisConnection connection, String pattern, List<String> batch) {
        batch.clear();
        try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(256).build())) {
            while (cursor.hasNext()) {
                batch.add(new String(cursor.next(), StandardCharsets.UTF_8));
                if (batch.size() >= 256) {
                    redisTemplate.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
            }
        }
    }

    private List<RoleSetting> resolveActiveRoles(Long userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserIdAndDeletedFlagFalse(userId);
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, RoleSetting> activeRoleMap = new LinkedHashMap<>();
        roleSettingRepository.findByIdInAndDeletedFlagFalse(roleIds).stream()
                .filter(role -> ROLE_STATUS_NORMAL.equals(role.getStatus()))
                .forEach(role -> activeRoleMap.put(role.getId(), role));
        return roleIds.stream()
                .map(activeRoleMap::get)
                .filter(Objects::nonNull)
                .toList();
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

        Set<Long> userIds = userAccountRepository.findByDepartmentIdAndDeletedFlagFalse(departmentId)
                .stream()
                .map(UserAccount::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
                .filter(ROLE_STATUS_NORMAL::equals)
                .isPresent();
    }

    private String buildPermissionSummary(Collection<RolePermission> permissions) {
        return ResourcePermissionCatalog.buildPermissionSummary(permissions);
    }

    private Set<String> splitActions(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> actions = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String action = ResourcePermissionCatalog.normalizeAction(item);
            if (!action.isBlank()) {
                actions.add(action);
            }
        }
        return actions;
    }

    private String permissionScopeKey(String resource, String action) {
        if (resource == null || resource.isBlank() || action == null || action.isBlank()) {
            return "";
        }
        return resource + ":" + action;
    }

    private String normalizePermissionScopeKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        int separator = key.indexOf(':');
        if (separator <= 0 || separator >= key.length() - 1) {
            return "";
        }
        String resource = ResourcePermissionCatalog.normalizeResource(key.substring(0, separator));
        String action = ResourcePermissionCatalog.normalizeAction(key.substring(separator + 1));
        return permissionScopeKey(resource, action);
    }

    private String parsePermissionScopeResource(String key) {
        String normalizedKey = normalizePermissionScopeKey(key);
        int separator = normalizedKey.indexOf(':');
        return separator <= 0 ? "" : normalizedKey.substring(0, separator);
    }

    private List<MenuSnapshot> loadActiveMenuSnapshots() {
        if (menuRepository == null) {
            return List.of();
        }
        if (redisJsonCacheSupport == null) {
            return menuRepository.findByStatusAndDeletedFlagFalseOrderBySortOrder("正常").stream()
                    .map(this::toMenuSnapshot)
                    .toList();
        }
        return redisJsonCacheSupport.getOrLoad(
                MENU_CACHE_KEY,
                MENU_CACHE_TTL,
                MENU_LIST_TYPE,
                () -> menuRepository.findByStatusAndDeletedFlagFalseOrderBySortOrder("正常").stream()
                        .map(this::toMenuSnapshot)
                        .toList()
        );
    }

    private MenuSnapshot toMenuSnapshot(Menu menu) {
        return new MenuSnapshot(
                menu.getMenuCode(),
                menu.getMenuName(),
                menu.getParentCode(),
                menu.getRoutePath(),
                menu.getIcon(),
                menu.getSortOrder(),
                menu.getMenuType(),
                menu.getStatus()
        );
    }

    private record MenuSnapshot(
            String menuCode,
            String menuName,
            String parentCode,
            String routePath,
            String icon,
            Integer sortOrder,
            String menuType,
            String status
    ) {
    }

    private record DepartmentCacheEntry(
            Set<Long> userIds,
            long expiresAt
    ) {
    }

    private record UserPermissionSnapshot(
            Map<String, Set<String>> permissionMap,
            Map<String, String> dataScopeByPermission
    ) {
    }
}
