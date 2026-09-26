package com.leo.erp.system.printtemplate.repository;

import com.leo.erp.system.printtemplate.domain.entity.ProjectPrintPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectPrintPreferenceRepository extends JpaRepository<ProjectPrintPreference, Long> {

    Optional<ProjectPrintPreference> findByProjectIdAndBillTypeAndDeletedFlagFalse(Long projectId, String billType);
}
