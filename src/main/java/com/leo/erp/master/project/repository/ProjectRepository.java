package com.leo.erp.master.project.repository;

import com.leo.erp.master.project.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    boolean existsByProjectCodeAndDeletedFlagFalse(String projectCode);

    List<Project> findByDeletedFlagFalseOrderByProjectCodeAsc();

    Optional<Project> findByIdAndDeletedFlagFalse(Long id);

    long countByDeletedFlagFalse();
}
