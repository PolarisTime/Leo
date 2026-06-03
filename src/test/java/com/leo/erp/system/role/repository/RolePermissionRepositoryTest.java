package com.leo.erp.system.role.repository;

import com.leo.erp.system.role.domain.entity.RolePermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionRepositoryTest {

    @Mock
    private RolePermissionRepository repository;

    @Test
    void findByRoleIdShouldReturnPermissions() {
        RolePermission permission = createPermission(1L, 10L, "material", "read");
        when(repository.findByRoleIdAndDeletedFlagFalse(10L)).thenReturn(List.of(permission));

        List<RolePermission> result = repository.findByRoleIdAndDeletedFlagFalse(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getResourceCode()).isEqualTo("material");
    }

    @Test
    void findByRoleIdShouldReturnEmptyWhenNone() {
        when(repository.findByRoleIdAndDeletedFlagFalse(999L)).thenReturn(List.of());

        List<RolePermission> result = repository.findByRoleIdAndDeletedFlagFalse(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void findByRoleIdInShouldReturnMatchingPermissions() {
        List<RolePermission> permissions = List.of(
                createPermission(1L, 10L, "material", "read"),
                createPermission(2L, 20L, "order", "read")
        );
        when(repository.findByRoleIdInAndDeletedFlagFalse(List.of(10L, 20L))).thenReturn(permissions);

        List<RolePermission> result = repository.findByRoleIdInAndDeletedFlagFalse(List.of(10L, 20L));

        assertThat(result).hasSize(2);
    }

    @Test
    void deleteActiveByRoleIdShouldReturnCount() {
        when(repository.deleteActiveByRoleId(10L)).thenReturn(3);

        int result = repository.deleteActiveByRoleId(10L);

        assertThat(result).isEqualTo(3);
    }

    private RolePermission createPermission(Long id, Long roleId, String resourceCode, String actionCode) {
        RolePermission permission = new RolePermission();
        permission.setId(id);
        permission.setRoleId(roleId);
        permission.setResourceCode(resourceCode);
        permission.setActionCode(actionCode);
        return permission;
    }
}
