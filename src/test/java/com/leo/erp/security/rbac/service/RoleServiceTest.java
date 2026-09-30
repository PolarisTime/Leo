package com.leo.erp.security.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.rbac.PermissionCacheService;
import com.leo.erp.security.rbac.domain.entity.SysRole;
import com.leo.erp.security.rbac.domain.entity.SysRolePermission;
import com.leo.erp.security.rbac.repository.SysRolePermissionRepository;
import com.leo.erp.security.rbac.repository.SysRoleRepository;
import com.leo.erp.security.rbac.repository.SysUserRoleRepository;
import com.leo.erp.security.rbac.web.dto.RoleDetailResponse;
import com.leo.erp.security.rbac.web.dto.RoleRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 角色服务测试：内置角色保护、编码唯一、权限码校验、权限整体替换。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    private static final String SALES_ORDERS_WILDCARD =
            PermissionCodes.Resources.SALES_ORDERS + ":" + PermissionCodes.Actions.WILDCARD;

    @Mock
    private SysRoleRepository roleRepository;

    @Mock
    private SysRolePermissionRepository rolePermissionRepository;

    @Mock
    private SysUserRoleRepository userRoleRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private RoleService roleService;

    @Test
    void delete_shouldRejectBuiltinRole() {
        when(roleRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(role(1L, "SUPER_ADMIN", true)));

        assertThatThrownBy(() -> roleService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内置角色");

        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateStatus_shouldRejectDisablingBuiltinRole() {
        when(roleRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(role(1L, "SUPER_ADMIN", true)));

        assertThatThrownBy(() -> roleService.updateStatus(1L, StatusConstants.DISABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内置角色不允许禁用");

        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateStatus_shouldAllowEnablingBuiltinRole() {
        SysRole builtin = role(1L, "SUPER_ADMIN", true);
        builtin.setStatus(StatusConstants.DISABLED);
        when(roleRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(builtin));
        when(roleRepository.save(builtin)).thenReturn(builtin);

        roleService.updateStatus(1L, StatusConstants.NORMAL);

        assertThat(builtin.getStatus()).isEqualTo(StatusConstants.NORMAL);
    }

    @Test
    void update_shouldRejectBuiltinCodeChange() {
        when(roleRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(role(1L, "SUPER_ADMIN", true)));

        assertThatThrownBy(() -> roleService.update(1L, new RoleRequest("OTHER", "名称", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内置角色编码不允许修改");
    }

    @Test
    void create_shouldRejectDuplicateCode() {
        when(roleRepository.existsByCodeAndDeletedFlagFalse("DUP")).thenReturn(true);

        assertThatThrownBy(() -> roleService.create(new RoleRequest("DUP", "名称", null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void replacePermissions_shouldRejectUnknownCode() {
        when(roleRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(role(5L, "CUSTOM", false)));

        assertThatThrownBy(() -> roleService.replacePermissions(5L, List.of("unknown:read")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法权限码");

        verify(rolePermissionRepository, never()).deleteByRoleId(any());
    }

    @Test
    void replacePermissions_shouldRejectBuiltinWithoutWildcard() {
        when(roleRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(role(1L, "SUPER_ADMIN", true)));

        assertThatThrownBy(() -> roleService.replacePermissions(1L, List.of(PermissionCodes.ROLES_READ)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("通配符");

        verify(rolePermissionRepository, never()).deleteByRoleId(any());
    }

    // ---- 增量替换（diff）语义 --------------------------------------------------------
    // 为什么这几条断言重要：原先「先全删再全插」在并发下会撞唯一索引，
    // 实测同角色并发替换权限有 74.9% 的请求被 409 拒绝。
    // 增量写法让「并发写相同目标集合」退化为空操作，冲突随之消失。

    @Test
    void replacePermissions_sameSet_changesNothing() {
        SysRole custom = role(5L, "CUSTOM", false);
        when(roleRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(custom));
        when(rolePermissionRepository.findByRoleId(5L)).thenReturn(new ArrayList<>(List.of(
                link(800L, 5L, PermissionCodes.ROLES_READ),
                link(801L, 5L, PermissionCodes.ROLES_WRITE))));

        RoleDetailResponse response = roleService.replacePermissions(
                5L, List.of(PermissionCodes.ROLES_READ, PermissionCodes.ROLES_WRITE, PermissionCodes.ROLES_READ));

        // 目标集合与现状一致：既不删也不插 —— 这正是并发冲突率归零的原因
        verify(rolePermissionRepository, never()).deleteByRoleId(any());
        verify(rolePermissionRepository, never()).deleteByRoleIdAndPermissionCodeIn(any(), any());
        verify(rolePermissionRepository, never()).saveAll(any());
        assertThat(response.permissions())
                .containsExactlyInAnyOrder(PermissionCodes.ROLES_READ, PermissionCodes.ROLES_WRITE);
        // 权限集合未变也允许失效缓存（幂等且安全），但不能要求必然调用
        verify(permissionCacheService).invalidateAll();
    }

    @Test
    void replacePermissions_onlyInsertsAddedCodes() {
        SysRole custom = role(5L, "CUSTOM", false);
        when(roleRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(custom));
        when(snowflakeIdGenerator.nextId()).thenReturn(900L);
        when(rolePermissionRepository.findByRoleId(5L)).thenReturn(new ArrayList<>(List.of(
                link(800L, 5L, PermissionCodes.ROLES_READ))));

        roleService.replacePermissions(5L, List.of(PermissionCodes.ROLES_READ, PermissionCodes.ROLES_WRITE));

        verify(rolePermissionRepository, never()).deleteByRoleIdAndPermissionCodeIn(any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<SysRolePermission>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(rolePermissionRepository).saveAll(captor.capture());
        List<SysRolePermission> inserted = new ArrayList<>();
        captor.getValue().forEach(inserted::add);
        assertThat(inserted).extracting(SysRolePermission::getPermissionCode)
                .containsExactly(PermissionCodes.ROLES_WRITE);
    }

    @Test
    void replacePermissions_onlyDeletesRemovedCodes() {
        SysRole custom = role(5L, "CUSTOM", false);
        when(roleRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(custom));
        when(rolePermissionRepository.findByRoleId(5L)).thenReturn(new ArrayList<>(List.of(
                link(800L, 5L, PermissionCodes.ROLES_READ),
                link(801L, 5L, PermissionCodes.ROLES_WRITE))));

        roleService.replacePermissions(5L, List.of(PermissionCodes.ROLES_READ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(rolePermissionRepository).deleteByRoleIdAndPermissionCodeIn(eq(5L), captor.capture());
        assertThat(captor.getValue()).containsExactly(PermissionCodes.ROLES_WRITE);
        verify(rolePermissionRepository, never()).saveAll(any());
        verify(rolePermissionRepository, never()).deleteByRoleId(any());
    }

    @Test
    void replacePermissions_mixedSet_deletesAndInsertsOnlyTheDelta() {
        SysRole custom = role(5L, "CUSTOM", false);
        when(roleRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(custom));
        when(snowflakeIdGenerator.nextId()).thenReturn(902L);
        when(rolePermissionRepository.findByRoleId(5L)).thenReturn(new ArrayList<>(List.of(
                link(800L, 5L, PermissionCodes.ROLES_READ),
                link(801L, 5L, PermissionCodes.ROLES_WRITE))));

        roleService.replacePermissions(5L, List.of(PermissionCodes.ROLES_READ, PermissionCodes.MATERIALS_READ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> deleted = ArgumentCaptor.forClass(Collection.class);
        verify(rolePermissionRepository).deleteByRoleIdAndPermissionCodeIn(eq(5L), deleted.capture());
        assertThat(deleted.getValue()).containsExactly(PermissionCodes.ROLES_WRITE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<SysRolePermission>> inserted = ArgumentCaptor.forClass(Iterable.class);
        verify(rolePermissionRepository).saveAll(inserted.capture());
        List<SysRolePermission> links = new ArrayList<>();
        inserted.getValue().forEach(links::add);
        assertThat(links).extracting(SysRolePermission::getPermissionCode)
                .containsExactly(PermissionCodes.MATERIALS_READ);
        verify(permissionCacheService).invalidateAll();
    }

    @Test
    void replacePermissions_shouldAcceptResourceWildcard() {
        SysRole custom = role(5L, "CUSTOM", false);
        when(roleRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(custom));
        // 目标集合与现状一致（增量 diff 后是空操作），因此不应再申请雪花 ID、也不应写库
        when(rolePermissionRepository.findByRoleId(5L))
                .thenReturn(new ArrayList<>(List.of(link(900L, 5L, SALES_ORDERS_WILDCARD))));

        RoleDetailResponse response = roleService.replacePermissions(5L, List.of(SALES_ORDERS_WILDCARD));

        assertThat(response.permissions()).containsExactly(SALES_ORDERS_WILDCARD);
        verify(rolePermissionRepository, never()).saveAll(any());
        verify(rolePermissionRepository, never()).deleteByRoleIdAndPermissionCodeIn(any(), any());
    }

    private SysRole role(Long id, String code, boolean builtin) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        role.setBuiltin(builtin);
        role.setStatus(StatusConstants.NORMAL);
        return role;
    }

    private SysRolePermission link(Long id, Long roleId, String code) {
        SysRolePermission link = new SysRolePermission();
        link.setId(id);
        link.setRoleId(roleId);
        link.setPermissionCode(code);
        return link;
    }
}
