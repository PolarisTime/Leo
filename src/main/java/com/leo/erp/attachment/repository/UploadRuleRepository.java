package com.leo.erp.attachment.repository;

import com.leo.erp.attachment.domain.entity.UploadRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadRuleRepository extends JpaRepository<UploadRule, Long> {

    Optional<UploadRule> findByRuleCodeAndDeletedFlagFalse(String ruleCode);

    Optional<UploadRule> findByModuleKeyAndDeletedFlagFalse(String moduleKey);

    List<UploadRule> findAllByDeletedFlagFalseOrderByIdAsc();
}
