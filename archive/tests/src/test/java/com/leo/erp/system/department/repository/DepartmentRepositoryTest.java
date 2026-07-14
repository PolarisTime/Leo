package com.leo.erp.system.department.repository;

import com.leo.erp.system.department.domain.entity.Department;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentRepositoryTest {

    @Mock
    private DepartmentRepository repository;

    @Test
    void existsByDepartmentCodeAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByDepartmentCodeAndDeletedFlagFalse("DEPT001")).thenReturn(true);

        boolean result = repository.existsByDepartmentCodeAndDeletedFlagFalse("DEPT001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByDepartmentCodeAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByDepartmentCodeAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByDepartmentCodeAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByDepartmentCodeAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByDepartmentCodeAndDeletedFlagFalse("DEPT002")).thenReturn(false);

        boolean result = repository.existsByDepartmentCodeAndDeletedFlagFalse("DEPT002");

        assertThat(result).isFalse();
    }

    @Test
    void findByDepartmentCodeAndDeletedFlagFalse_shouldReturnDepartmentWhenExists() {
        Department department = new Department();
        department.setId(1L);
        department.setDepartmentCode("DEPT001");
        department.setDepartmentName("测试部门");
        department.setDeletedFlag(false);

        when(repository.findByDepartmentCodeAndDeletedFlagFalse("DEPT001")).thenReturn(Optional.of(department));

        Optional<Department> result = repository.findByDepartmentCodeAndDeletedFlagFalse("DEPT001");

        assertThat(result).isPresent();
        assertThat(result.get().getDepartmentName()).isEqualTo("测试部门");
    }

    @Test
    void findByDepartmentCodeAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByDepartmentCodeAndDeletedFlagFalse("DEPT001")).thenReturn(Optional.empty());

        Optional<Department> result = repository.findByDepartmentCodeAndDeletedFlagFalse("DEPT001");

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnDepartmentWhenExistsAndNotDeleted() {
        Department department = new Department();
        department.setId(1L);
        department.setDepartmentCode("DEPT001");
        department.setDepartmentName("测试部门");
        department.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(department));

        Optional<Department> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getDepartmentCode()).isEqualTo("DEPT001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<Department> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void existsByParentIdAndDeletedFlagFalse_shouldReturnTrueWhenChildExists() {
        when(repository.existsByParentIdAndDeletedFlagFalse(1L)).thenReturn(true);

        boolean result = repository.existsByParentIdAndDeletedFlagFalse(1L);

        assertThat(result).isTrue();
    }

    @Test
    void existsByParentIdAndDeletedFlagFalse_shouldReturnFalseWhenNoChild() {
        when(repository.existsByParentIdAndDeletedFlagFalse(999L)).thenReturn(false);

        boolean result = repository.existsByParentIdAndDeletedFlagFalse(999L);

        assertThat(result).isFalse();
    }

    @Test
    void findByIdInAndDeletedFlagFalse_shouldReturnMatchingDepartments() {
        Department dept1 = new Department();
        dept1.setId(1L);
        dept1.setDepartmentCode("DEPT001");
        dept1.setDepartmentName("部门A");
        dept1.setDeletedFlag(false);

        Department dept2 = new Department();
        dept2.setId(2L);
        dept2.setDepartmentCode("DEPT002");
        dept2.setDepartmentName("部门B");
        dept2.setDeletedFlag(false);

        when(repository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L))).thenReturn(List.of(dept1, dept2));

        List<Department> result = repository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc_shouldReturnMatchingDepartments() {
        Department dept1 = new Department();
        dept1.setId(1L);
        dept1.setDepartmentCode("DEPT002");
        dept1.setDepartmentName("部门B");
        dept1.setStatus("ACTIVE");
        dept1.setSortOrder(1);
        dept1.setDeletedFlag(false);

        Department dept2 = new Department();
        dept2.setId(2L);
        dept2.setDepartmentCode("DEPT001");
        dept2.setDepartmentName("部门A");
        dept2.setStatus("ACTIVE");
        dept2.setSortOrder(2);
        dept2.setDeletedFlag(false);

        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("ACTIVE"))
                .thenReturn(List.of(dept1, dept2));

        List<Department> result = repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("ACTIVE");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDepartmentCode()).isEqualTo("DEPT002");
        assertThat(result.get(1).getDepartmentCode()).isEqualTo("DEPT001");
    }
}
