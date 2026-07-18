package com.leo.erp.system.generalsetting.repository;

import com.leo.erp.system.generalsetting.domain.entity.GeneralSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GeneralSettingRepository extends JpaRepository<GeneralSetting, Long> {

    Optional<GeneralSetting> findBySettingCodeAndDeletedFlagFalse(String settingCode);
}
