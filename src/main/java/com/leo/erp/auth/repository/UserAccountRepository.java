package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.List;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByLoginName(String loginName);

    Optional<UserAccount> findByLoginNameAndDeletedFlagFalse(String loginName);

    Optional<UserAccount> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByLoginName(String loginName);

    boolean existsByLoginNameAndDeletedFlagFalse(String loginName);

    long countByDepartmentIdAndDeletedFlagFalse(Long departmentId);

    List<UserAccount> findByDepartmentIdAndDeletedFlagFalse(Long departmentId);

    List<UserAccount> findByTotpSecretIsNotNullAndDeletedFlagFalse();
}
