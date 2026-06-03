package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleRepositoryTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Test
    void shouldFindByUserIdAndDeletedFlagFalse() {
        UserRole userRole = new UserRole();
        userRole.setId(1L);
        userRole.setUserId(100L);
        userRole.setRoleId(200L);

        when(userRoleRepository.findByUserIdAndDeletedFlagFalse(100L)).thenReturn(List.of(userRole));

        List<UserRole> result = userRoleRepository.findByUserIdAndDeletedFlagFalse(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(100L);
        assertThat(result.get(0).getRoleId()).isEqualTo(200L);
    }

    @Test
    void shouldDeleteByUserIdAndDeletedFlagFalse() {
        userRoleRepository.deleteByUserIdAndDeletedFlagFalse(100L);

        verify(userRoleRepository).deleteByUserIdAndDeletedFlagFalse(100L);
    }

    @Test
    void shouldFindByRoleIdInAndDeletedFlagFalse() {
        UserRole userRole1 = new UserRole();
        userRole1.setId(1L);
        userRole1.setRoleId(200L);

        UserRole userRole2 = new UserRole();
        userRole2.setId(2L);
        userRole2.setRoleId(201L);

        when(userRoleRepository.findByRoleIdInAndDeletedFlagFalse(List.of(200L, 201L)))
                .thenReturn(List.of(userRole1, userRole2));

        List<UserRole> result = userRoleRepository.findByRoleIdInAndDeletedFlagFalse(List.of(200L, 201L));

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldCountByRoleIdAndDeletedFlagFalse() {
        when(userRoleRepository.countByRoleIdAndDeletedFlagFalse(200L)).thenReturn(5L);

        long count = userRoleRepository.countByRoleIdAndDeletedFlagFalse(200L);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void shouldCountActiveUsersByRoleId() {
        when(userRoleRepository.countActiveUsersByRoleId(200L)).thenReturn(3L);

        long count = userRoleRepository.countActiveUsersByRoleId(200L);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void shouldFindFirstActiveUserByRoleId() {
        when(userRoleRepository.findFirstActiveUserByRoleId(200L)).thenReturn(Optional.empty());

        Optional<?> result = userRoleRepository.findFirstActiveUserByRoleId(200L);

        assertThat(result).isEmpty();
    }
}
