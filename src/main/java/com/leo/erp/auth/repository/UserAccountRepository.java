package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.UserAccount;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByLoginNameAndDeletedFlagFalse(String loginName);

    Optional<UserAccount> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByDeletedFlagFalse();

    boolean existsByLoginNameAndDeletedFlagFalse(String loginName);

    /**
     * 原子更新「最后登录时间」，不参与乐观锁版本比对。
     *
     * <p>用法背景见 {@code LoginAuditService}：登录不再改实体、也不再对 {@code sys_user}
     * 加悲观锁，审计字段用这一条 UPDATE 直写即可，因此不会产生版本冲突。</p>
     *
     * <p><b>不要加 {@code clearAutomatically = true}。</b>实测该标志会把持久化上下文清空，
     * 使同一个登录事务里随后对 {@code sys_user} 的悲观锁读变成「重新加载 + 锁升级」，
     * 触发 Hibernate 追加一条整行 {@code update sys_user ... version=?}：
     * 它既用旧快照覆盖刚写好的审计值，又用陈旧版本做 {@code where version=?}（并发时 409）。
     * 与 {@code flushAutomatically} 一起使用正是当时踩坑的组合。</p>
     */
    @Modifying
    @Query("UPDATE UserAccount u SET u.lastLoginDate = :loginAt WHERE u.id = :userId AND u.deletedFlag = false")
    int updateLastLoginDate(@Param("userId") Long userId, @Param("loginAt") LocalDateTime loginAt);

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
