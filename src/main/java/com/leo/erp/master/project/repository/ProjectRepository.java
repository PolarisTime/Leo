package com.leo.erp.master.project.repository;

import com.leo.erp.master.project.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    boolean existsByProjectCodeAndDeletedFlagFalse(String projectCode);

    List<Project> findByDeletedFlagFalseOrderByProjectCodeAsc();

    List<Project> findByCustomerCodeAndProjectNameAndDeletedFlagFalseOrderByProjectCodeAsc(String customerCode,
                                                                                           String projectName);

    @Query("""
            select project
            from Project project
            where project.deletedFlag = false
              and project.status = :status
              and (
                    project.customerId = :customerId
                    or (project.customerId is null and project.customerCode = :customerCode)
              )
            order by project.projectCode asc
            """)
    List<Project> findActiveOptionsByCustomerIdentity(@Param("customerId") Long customerId,
                                                       @Param("customerCode") String customerCode,
                                                       @Param("status") String status);

    Optional<Project> findByIdAndDeletedFlagFalse(Long id);

    long countByDeletedFlagFalse();
}
