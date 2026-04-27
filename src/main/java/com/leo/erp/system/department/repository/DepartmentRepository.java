package com.leo.erp.system.department.repository;

import com.leo.erp.system.department.domain.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {

    boolean existsByDepartmentCodeAndDeletedFlagFalse(String departmentCode);

    Optional<Department> findByDepartmentCodeAndDeletedFlagFalse(String departmentCode);

    Optional<Department> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByParentIdAndDeletedFlagFalse(Long parentId);

    List<Department> findByIdInAndDeletedFlagFalse(Collection<Long> ids);

    List<Department> findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc(String status);
}
