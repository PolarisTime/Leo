package com.leo.erp.system.generalsetting.repository;

import com.leo.erp.system.generalsetting.domain.entity.GeneralSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GeneralSettingRepository
        extends JpaRepository<GeneralSetting, Long>, JpaSpecificationExecutor<GeneralSetting> {

    boolean existsBySettingCodeAndDeletedFlagFalse(String settingCode);

    Optional<GeneralSetting> findByIdAndDeletedFlagFalse(Long id);

    Optional<GeneralSetting> findBySettingCodeAndDeletedFlagFalse(String settingCode);

    List<GeneralSetting> findBySettingCodeInAndDeletedFlagFalse(Collection<String> settingCodes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select setting from GeneralSetting setting
            where setting.settingCode = :settingCode and setting.deletedFlag = false
            """)
    Optional<GeneralSetting> findBySettingCodeAndDeletedFlagFalseForUpdate(@Param("settingCode") String settingCode);
}
