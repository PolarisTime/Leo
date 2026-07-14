package com.leo.erp.security.permission;

import com.leo.erp.auth.domain.entity.UserRole;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PermissionResolverTest {

    @Test
    void shouldReturnEmptySnapshotWhenUserHasNoRoles() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        when(userRoleRepository.findByUserIdAndDeletedFlagFalse(1001L)).thenReturn(List.of());
        when(cache.read(1001L)).thenReturn(null);

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        UserPermissionSnapshot snapshot = resolver.getUserPermissionSnapshot(1001L);

        assertThat(snapshot.permissionMap()).isEmpty();
        assertThat(snapshot.dataScopeByPermission()).isEmpty();
    }

    @Test
    void shouldReturnCachedSnapshotWhenAvailable() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        UserPermissionSnapshot cached = new UserPermissionSnapshot(
                Map.of("material", Set.of("read", "write")),
                Map.of("material:read", "department")
        );
        when(cache.read(1001L)).thenReturn(cached);

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        UserPermissionSnapshot snapshot = resolver.getUserPermissionSnapshot(1001L);

        assertThat(snapshot).isSameAs(cached);
    }

    @Test
    void shouldReturnPermissionMapFromSnapshot() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        Map<String, Set<String>> permissionMap = Map.of("material", Set.of("read"));
        when(cache.read(1001L)).thenReturn(new UserPermissionSnapshot(permissionMap, Map.of()));

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        Map<String, Set<String>> result = resolver.getUserPermissionMap(1001L);

        assertThat(result).isSameAs(permissionMap);
    }

    @Test
    void shouldResolvePermissionsFromRoles() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        UserRole userRole = new UserRole();
        userRole.setRoleId(1L);
        when(userRoleRepository.findByUserIdAndDeletedFlagFalse(1001L)).thenReturn(List.of(userRole));
        when(cache.read(1001L)).thenReturn(null);

        RoleSetting role = new RoleSetting();
        role.setId(1L);
        role.setStatus(StatusConstants.NORMAL);
        role.setDataScope("department");
        when(roleSettingRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(role));

        RolePermission permission = new RolePermission();
        permission.setRoleId(1L);
        permission.setResourceCode("material");
        permission.setActionCode("read");
        when(rolePermissionRepository.findByRoleIdInAndDeletedFlagFalse(List.of(1L))).thenReturn(List.of(permission));

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        UserPermissionSnapshot snapshot = resolver.getUserPermissionSnapshot(1001L);

        assertThat(snapshot.permissionMap()).containsKey("material");
        assertThat(snapshot.permissionMap().get("material")).contains("read");
        assertThat(snapshot.dataScopeByPermission()).containsKey("material:read");
    }

    @Test
    void shouldResolveAncestorRoles() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        UserRole userRole = new UserRole();
        userRole.setRoleId(2L);
        when(userRoleRepository.findByUserIdAndDeletedFlagFalse(1001L)).thenReturn(List.of(userRole));
        when(cache.read(1001L)).thenReturn(null);

        RoleSetting childRole = new RoleSetting();
        childRole.setId(2L);
        childRole.setParentId(1L);
        childRole.setStatus(StatusConstants.NORMAL);
        childRole.setDataScope("self");

        RoleSetting parentRole = new RoleSetting();
        parentRole.setId(1L);
        parentRole.setStatus(StatusConstants.NORMAL);
        parentRole.setDataScope("department");

        when(roleSettingRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(childRole));
        when(roleSettingRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(parentRole));

        RolePermission childPermission = new RolePermission();
        childPermission.setRoleId(2L);
        childPermission.setResourceCode("material");
        childPermission.setActionCode("read");

        RolePermission parentPermission = new RolePermission();
        parentPermission.setRoleId(1L);
        parentPermission.setResourceCode("material");
        parentPermission.setActionCode("write");

        when(rolePermissionRepository.findByRoleIdInAndDeletedFlagFalse(List.of(2L, 1L)))
                .thenReturn(List.of(childPermission, parentPermission));

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        List<RoleSetting> roles = resolver.resolveActiveRoles(1001L);

        assertThat(roles).hasSize(2);
        assertThat(roles.get(0).getId()).isEqualTo(2L);
        assertThat(roles.get(1).getId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnEmptyRolesWhenUserRoleIdsAreNull() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        when(userRoleRepository.findByUserIdAndDeletedFlagFalse(1001L))
                .thenReturn(List.of(userRole(null), userRole(null)));

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        List<RoleSetting> roles = resolver.resolveActiveRoles(1001L);

        assertThat(roles).isEmpty();
        verifyNoInteractions(roleSettingRepository, rolePermissionRepository);
    }

    @Test
    void shouldSkipDuplicateAncestorRole() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        when(userRoleRepository.findByUserIdAndDeletedFlagFalse(1001L))
                .thenReturn(List.of(userRole(2L), userRole(3L)));
        when(roleSettingRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(role(2L, 1L)));
        when(roleSettingRepository.findByIdAndDeletedFlagFalse(3L)).thenReturn(Optional.of(role(3L, 1L)));
        when(roleSettingRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(role(1L, null)));

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        List<RoleSetting> roles = resolver.resolveActiveRoles(1001L);

        assertThat(roles).extracting(RoleSetting::getId).containsExactly(2L, 3L, 1L);
        verify(roleSettingRepository, times(1)).findByIdAndDeletedFlagFalse(1L);
    }

    @Test
    void shouldNotEnqueueParentWhenAlreadyVisited() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        when(userRoleRepository.findByUserIdAndDeletedFlagFalse(1001L))
                .thenReturn(List.of(userRole(1L), userRole(2L)));
        when(roleSettingRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(role(1L, null)));
        when(roleSettingRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(role(2L, 1L)));

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        List<RoleSetting> roles = resolver.resolveActiveRoles(1001L);

        assertThat(roles).extracting(RoleSetting::getId).containsExactly(1L, 2L);
        verify(roleSettingRepository, times(1)).findByIdAndDeletedFlagFalse(1L);
    }

    @Test
    void shouldReturnEmptySummaryForNullRoles() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        String summary = resolver.getPermissionSummaryForRoles(null);

        assertThat(summary).isEmpty();
    }

    @Test
    void shouldReturnEmptySummaryForEmptyRoles() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        String summary = resolver.getPermissionSummaryForRoles(List.of());

        assertThat(summary).isEmpty();
    }

    @Test
    void shouldReturnEmptySummaryWhenRolesHaveOnlyNullIds() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        RoleSettingRepository roleSettingRepository = mock(RoleSettingRepository.class);
        PermissionCache cache = mock(PermissionCache.class);
        RoleSetting role = new RoleSetting();

        PermissionResolver resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);

        String summary = resolver.getPermissionSummaryForRoles(List.of(role));

        assertThat(summary).isEmpty();
        verifyNoInteractions(rolePermissionRepository);
    }

    private UserRole userRole(Long roleId) {
        UserRole userRole = new UserRole();
        userRole.setRoleId(roleId);
        return userRole;
    }

    private RoleSetting role(Long id, Long parentId) {
        RoleSetting role = new RoleSetting();
        role.setId(id);
        role.setParentId(parentId);
        role.setStatus(StatusConstants.NORMAL);
        role.setDataScope(ResourcePermissionCatalog.SCOPE_SELF);
        return role;
    }
}
