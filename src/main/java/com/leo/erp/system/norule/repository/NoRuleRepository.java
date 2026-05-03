package com.leo.erp.system.norule.repository;

import com.leo.erp.system.norule.domain.entity.NoRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoRuleRepository extends JpaRepository<NoRule, Long>, JpaSpecificationExecutor<NoRule> {

    boolean existsBySettingCodeAndDeletedFlagFalse(String settingCode);

    Optional<NoRule> findByIdAndDeletedFlagFalse(Long id);

    Optional<NoRule> findBySettingCodeAndDeletedFlagFalse(String settingCode);

    List<NoRule> findBySettingCodeInAndDeletedFlagFalse(Collection<String> settingCodes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from NoRule rule where rule.settingCode = :settingCode and rule.deletedFlag = false")
    Optional<NoRule> findBySettingCodeAndDeletedFlagFalseForUpdate(@Param("settingCode") String settingCode);
}
