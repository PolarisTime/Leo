package com.leo.erp.master.project.repository;

import com.leo.erp.master.project.domain.entity.ProjectPriceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectPriceRuleRepository extends JpaRepository<ProjectPriceRule, Long> {

    List<ProjectPriceRule> findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(Long projectId);

    List<ProjectPriceRule> findByProjectIdInAndDeletedFlagFalseOrderBySortOrderAscIdAsc(
            List<Long> projectIds);

    boolean existsByProjectIdAndNameAndDeletedFlagFalse(Long projectId, String name);
}
