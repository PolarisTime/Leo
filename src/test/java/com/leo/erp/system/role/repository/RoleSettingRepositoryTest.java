package com.leo.erp.system.role.repository;

import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleSettingRepositoryTest {

    @Mock
    private RoleSettingRepository repository;

    @Test
    void existsByRoleCodeAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByRoleCodeAndDeletedFlagFalse("ROLE001")).thenReturn(true);

        boolean result = repository.existsByRoleCodeAndDeletedFlagFalse("ROLE001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByRoleCodeAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByRoleCodeAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByRoleCodeAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByRoleCodeAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByRoleCodeAndDeletedFlagFalse("ROLE002")).thenReturn(false);

        boolean result = repository.existsByRoleCodeAndDeletedFlagFalse("ROLE002");

        assertThat(result).isFalse();
    }

    @Test
    void findByRoleCodeAndDeletedFlagFalse_shouldReturnRoleWhenExists() {
        RoleSetting role = new RoleSetting();
        role.setId(1L);
        role.setRoleCode("ROLE001");
        role.setRoleName("测试角色");
        role.setDeletedFlag(false);

        when(repository.findByRoleCodeAndDeletedFlagFalse("ROLE001")).thenReturn(Optional.of(role));

        Optional<RoleSetting> result = repository.findByRoleCodeAndDeletedFlagFalse("ROLE001");

        assertThat(result).isPresent();
        assertThat(result.get().getRoleName()).isEqualTo("测试角色");
    }

    @Test
    void findByRoleCodeAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByRoleCodeAndDeletedFlagFalse("ROLE001")).thenReturn(Optional.empty());

        Optional<RoleSetting> result = repository.findByRoleCodeAndDeletedFlagFalse("ROLE001");

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdInAndDeletedFlagFalse_shouldReturnMatchingRoles() {
        RoleSetting role1 = new RoleSetting();
        role1.setId(1L);
        role1.setRoleCode("ROLE001");
        role1.setRoleName("角色A");
        role1.setDeletedFlag(false);

        RoleSetting role2 = new RoleSetting();
        role2.setId(2L);
        role2.setRoleCode("ROLE002");
        role2.setRoleName("角色B");
        role2.setDeletedFlag(false);

        when(repository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L))).thenReturn(List.of(role1, role2));

        List<RoleSetting> result = repository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByRoleCodeInAndDeletedFlagFalse_shouldReturnMatchingRoles() {
        RoleSetting role1 = new RoleSetting();
        role1.setId(1L);
        role1.setRoleCode("ROLE001");
        role1.setRoleName("角色A");
        role1.setDeletedFlag(false);

        RoleSetting role2 = new RoleSetting();
        role2.setId(2L);
        role2.setRoleCode("ROLE002");
        role2.setRoleName("角色B");
        role2.setDeletedFlag(false);

        when(repository.findByRoleCodeInAndDeletedFlagFalse(List.of("ROLE001", "ROLE002", "ROLE003")))
                .thenReturn(List.of(role1, role2));

        List<RoleSetting> result = repository.findByRoleCodeInAndDeletedFlagFalse(
                List.of("ROLE001", "ROLE002", "ROLE003"));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByRoleNameInAndDeletedFlagFalse_shouldReturnMatchingRoles() {
        RoleSetting role1 = new RoleSetting();
        role1.setId(1L);
        role1.setRoleCode("ROLE001");
        role1.setRoleName("角色A");
        role1.setDeletedFlag(false);

        RoleSetting role2 = new RoleSetting();
        role2.setId(2L);
        role2.setRoleCode("ROLE002");
        role2.setRoleName("角色B");
        role2.setDeletedFlag(false);

        when(repository.findByRoleNameInAndDeletedFlagFalse(List.of("角色A", "角色B", "角色C")))
                .thenReturn(List.of(role1, role2));

        List<RoleSetting> result = repository.findByRoleNameInAndDeletedFlagFalse(
                List.of("角色A", "角色B", "角色C"));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnRoleWhenExistsAndNotDeleted() {
        RoleSetting role = new RoleSetting();
        role.setId(1L);
        role.setRoleCode("ROLE001");
        role.setRoleName("测试角色");
        role.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(role));

        Optional<RoleSetting> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getRoleCode()).isEqualTo("ROLE001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<RoleSetting> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }
}
