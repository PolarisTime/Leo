package com.leo.erp.auth.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code updateLastLoginDate} 的 WHERE 条件守护测试（P2，2026-09-30 审计报告发现 1）。
 *
 * <p>无法在纯单测里连库执行，因此守护查询原文：节流阈值必须并入 WHERE，让 PostgreSQL 在
 * READ COMMITTED 下用 EvalPlanQual 以最新提交版本重评（并发突发实际只写 1 条）；
 * 同时守住两条历史教训——UPDATE 不得参与乐观锁比对、不得开启 clearAutomatically。</p>
 */
class UserAccountRepositoryQueryTest {

    private static Method updateLastLoginDateMethod() throws NoSuchMethodException {
        return UserAccountRepository.class.getMethod(
                "updateLastLoginDate", Long.class, LocalDateTime.class, LocalDateTime.class);
    }

    private static String normalizedQuery() throws NoSuchMethodException {
        Query query = updateLastLoginDateMethod().getAnnotation(Query.class);
        assertThat(query).as("updateLastLoginDate 必须是显式 @Query").isNotNull();
        return query.value().replaceAll("\\s+", " ").trim();
    }

    @Test
    void throttleConditionIsPartOfWhere_nullOrStaleRowOnly() throws NoSuchMethodException {
        String query = normalizedQuery();

        // 节流进 WHERE：lastLoginDate 为空（首登）或早于 staleBefore（超期）才允许写入
        assertThat(query).contains("u.lastLoginDate IS NULL");
        assertThat(query).contains("u.lastLoginDate < :staleBefore");
        assertThat(query).contains("u.id = :userId");
        assertThat(query).contains("u.deletedFlag = false");
        assertThat(query).contains("SET u.lastLoginDate = :loginAt");
    }

    @Test
    void auditUpdateNeverTouchesOptimisticLockColumn() throws NoSuchMethodException {
        // JPQL 批量 UPDATE 不比对也不自增 version：审计写入绝不允许把 version 拖进来
        assertThat(normalizedQuery()).doesNotContain("version");
    }

    @Test
    void modifyingMustNotClearPersistenceContext() throws NoSuchMethodException {
        // 实测坑：clearAutomatically/flushAutomatically 会触发 Hibernate 整行回写 sys_user
        Modifying modifying = updateLastLoginDateMethod().getAnnotation(Modifying.class);
        assertThat(modifying).as("updateLastLoginDate 必须是 @Modifying 批量更新").isNotNull();
        assertThat(modifying.clearAutomatically()).isFalse();
        assertThat(modifying.flushAutomatically()).isFalse();
    }
}
