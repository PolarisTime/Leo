package com.leo.erp.system.oss.repository;

import com.leo.erp.system.oss.domain.entity.OssSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OssSettingRepository extends JpaRepository<OssSetting, Long> {

    Optional<OssSetting> findFirstByDeletedFlagFalseOrderByIdAsc();

    List<OssSetting> findByEncryptedSecretKeyIsNotNullAndDeletedFlagFalse();
}
