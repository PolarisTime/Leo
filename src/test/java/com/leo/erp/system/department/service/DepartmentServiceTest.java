package com.leo.erp.system.department.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.department.web.dto.DepartmentRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepartmentServiceTest {

    @Test
    void shouldRejectDeleteWhenDepartmentHasBoundUsers() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department department = department();

        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));
        when(departmentRepository.existsByParentIdAndDeletedFlagFalse(10L)).thenReturn(false);
        when(userAccountRepository.countByDepartmentIdAndDeletedFlagFalse(10L)).thenReturn(1L);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                null
        );

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门已绑定用户");
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void shouldSyncBoundUserDepartmentNameWhenDepartmentNameChanges() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department department = department();
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setDepartmentId(10L);
        user.setDepartmentName("旧名称");

        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.of(department));
        when(departmentRepository.save(department)).thenReturn(department);
        when(userAccountRepository.findByDepartmentIdAndDeletedFlagFalse(10L)).thenReturn(List.of(user));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                null
        );

        service.update(10L, new DepartmentRequest(
                "HQ",
                "总部运营部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        ));

        assertThat(user.getDepartmentName()).isEqualTo("总部运营部");
        verify(userAccountRepository).saveAll(anyList());
        verify(permissionService).evictDepartmentUserCache(10L);
    }

    private Department department() {
        Department department = new Department();
        department.setId(10L);
        department.setDepartmentCode("HQ");
        department.setDepartmentName("总部");
        department.setStatus("正常");
        return department;
    }
}
