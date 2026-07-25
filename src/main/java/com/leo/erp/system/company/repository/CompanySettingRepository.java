package com.leo.erp.system.company.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanySettingRepository extends JpaRepository<CompanySetting, Long>,
        JpaSpecificationExecutor<CompanySetting>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return "company-setting";
    }

    @Override
    default boolean existsActive(Long recordId) {
        return existsByIdAndDeletedFlagFalse(recordId);
    }

    @Override
    default boolean lockActive(Long recordId) {
        return findActiveForAttachmentBinding(recordId).isPresent();
    }

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select company from CompanySetting company where company.id = :id and company.deletedFlag = false")
    Optional<CompanySetting> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    Optional<CompanySetting> findByIdAndDeletedFlagFalse(Long id);

    Optional<CompanySetting> findFirstByDeletedFlagFalseOrderByIdAsc();

    Optional<CompanySetting> findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(String status);

    Optional<CompanySetting> findByIdAndStatusAndDeletedFlagFalse(Long id, String status);

    List<CompanySetting> findByStatusAndDeletedFlagFalseOrderByIdAsc(String status);

    boolean existsByDeletedFlagFalse();

    boolean existsByCompanyNameAndDeletedFlagFalse(String companyName);
}
