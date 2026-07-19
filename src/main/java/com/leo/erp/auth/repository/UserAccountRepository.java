package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.UserAccount;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByLoginName(String loginName);

    Optional<UserAccount> findByLoginNameAndDeletedFlagFalse(String loginName);

    Optional<UserAccount> findByIdAndDeletedFlagFalse(Long id);

    Optional<UserAccount> findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(
            com.leo.erp.auth.domain.enums.UserStatus status
    );

    boolean existsByStatusAndDeletedFlagFalse(com.leo.erp.auth.domain.enums.UserStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.status = :status AND u.deletedFlag = false ORDER BY u.id")
    List<UserAccount> findActiveUsersForUpdate(
            @Param("status") com.leo.erp.auth.domain.enums.UserStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.id = :id AND u.deletedFlag = false")
    Optional<UserAccount> findByIdAndDeletedFlagFalseForUpdate(@Param("id") Long id);

    @Query("""
            SELECT u.credentialVersion AS credentialVersion
              FROM UserAccount u
             WHERE u.id = :id
               AND u.deletedFlag = false
               AND u.status = :status
            """)
    Optional<CredentialVersionProjection> findCredentialVersion(
            @Param("id") Long id,
            @Param("status") com.leo.erp.auth.domain.enums.UserStatus status
    );

    boolean existsByLoginName(String loginName);

    boolean existsByLoginNameAndDeletedFlagFalse(String loginName);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE sys_user
               SET preferences_json = CAST(:preferencesJson AS jsonb)
             WHERE id = :id
               AND deleted_flag = false
            """, nativeQuery = true)
    int updatePreferencesJson(@Param("id") Long id, @Param("preferencesJson") String preferencesJson);

    interface CredentialVersionProjection {
        Long getCredentialVersion();
    }
}
