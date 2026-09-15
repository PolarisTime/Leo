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

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByLoginNameAndDeletedFlagFalse(String loginName);

    Optional<UserAccount> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByDeletedFlagFalse();

    boolean existsByLoginNameAndDeletedFlagFalse(String loginName);

    /**
     * 统计“启用且拥有指定通配权限”的管理员账号数。
     *
     * <p>用于多用户删除/停用前的兜底校验，避免系统失去全部具备管理权限的启用账号。</p>
     */
    @Query(value = """
            SELECT COUNT(DISTINCT u.id)
              FROM sys_user u
              JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted_flag = false
              JOIN sys_role r ON r.id = ur.role_id AND r.deleted_flag = false AND r.status = :activeRoleStatus
              JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted_flag = false
             WHERE u.deleted_flag = false
               AND u.status = 'NORMAL'
               AND rp.permission_code = :wildcard
            """, nativeQuery = true)
    long countActiveAdminsWithPermission(@Param("wildcard") String wildcard,
                                         @Param("activeRoleStatus") String activeRoleStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.id = :id AND u.deletedFlag = false")
    Optional<UserAccount> findByIdAndDeletedFlagFalseForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE sys_user
               SET preferences_json = CAST(:preferencesJson AS jsonb)
             WHERE id = :id
               AND deleted_flag = false
            """, nativeQuery = true)
    int updatePreferencesJson(@Param("id") Long id, @Param("preferencesJson") String preferencesJson);

}
