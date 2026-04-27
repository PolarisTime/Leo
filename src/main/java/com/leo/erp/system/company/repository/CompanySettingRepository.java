package com.leo.erp.system.company.repository;

import com.leo.erp.system.company.domain.entity.CompanySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CompanySettingRepository extends JpaRepository<CompanySetting, Long>, JpaSpecificationExecutor<CompanySetting> {

    Optional<CompanySetting> findByIdAndDeletedFlagFalse(Long id);

    Optional<CompanySetting> findFirstByDeletedFlagFalseOrderByIdAsc();

    Optional<CompanySetting> findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(String status);

    boolean existsByDeletedFlagFalse();

    boolean existsByCompanyNameAndDeletedFlagFalse(String companyName);
}
