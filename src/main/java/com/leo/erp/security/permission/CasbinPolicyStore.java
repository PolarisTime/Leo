package com.leo.erp.security.permission;

import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.main.SyncedEnforcer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class CasbinPolicyStore {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final SyncedEnforcer enforcer;

    public CasbinPolicyStore(JdbcTemplate jdbcTemplate,
                             NamedParameterJdbcTemplate namedJdbcTemplate,
                             SyncedEnforcer enforcer) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.enforcer = enforcer;
    }

    public List<CasbinPolicy> findPermissionsByRole(String roleCode) {
        return jdbcTemplate.query(
                """
                SELECT v1, v2
                FROM casbin_rule
                WHERE ptype = 'p' AND v0 = ?
                ORDER BY v1, v2
                """,
                (resultSet, rowNum) -> new CasbinPolicy(resultSet.getString("v1"), resultSet.getString("v2")),
                roleCode
        );
    }

    public Map<String, List<CasbinPolicy>> findPermissionsByRoles(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, List<CasbinPolicy>> result = new LinkedHashMap<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource("roleCodes", roleCodes);
        namedJdbcTemplate.query(
                """
                SELECT v0, v1, v2
                FROM casbin_rule
                WHERE ptype = 'p' AND v0 IN (:roleCodes)
                ORDER BY v0, v1, v2
                """,
                parameters,
                (RowCallbackHandler) resultSet -> result.computeIfAbsent(resultSet.getString("v0"), key -> new java.util.ArrayList<>())
                        .add(new CasbinPolicy(resultSet.getString("v1"), resultSet.getString("v2")))
        );
        return result;
    }

    public List<String> findDirectRoleCodes(String subject) {
        return jdbcTemplate.queryForList(
                """
                SELECT v1
                FROM casbin_rule
                WHERE ptype = 'g' AND v0 = ?
                ORDER BY id
                """,
                String.class,
                subject
        );
    }

    public Map<String, Long> countUsersByRoles(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("roleCodes", roleCodes);
        Map<String, Long> result = new LinkedHashMap<>();
        namedJdbcTemplate.query(
                """
                SELECT rule.v1 AS role_code, COUNT(DISTINCT account.id) AS user_count
                FROM casbin_rule rule
                JOIN sys_user account ON account.id::text = rule.v0
                WHERE rule.ptype = 'g'
                  AND rule.v1 IN (:roleCodes)
                  AND account.deleted_flag = false
                GROUP BY rule.v1
                """,
                parameters,
                (RowCallbackHandler) resultSet -> result.put(resultSet.getString("role_code"), resultSet.getLong("user_count"))
        );
        return result;
    }

    public List<Long> findUserIdsByRole(String roleCode) {
        return jdbcTemplate.queryForList(
                """
                SELECT account.id
                FROM casbin_rule rule
                JOIN sys_user account ON account.id::text = rule.v0
                WHERE rule.ptype = 'g'
                  AND rule.v1 = ?
                  AND account.deleted_flag = false
                ORDER BY account.id
                """,
                Long.class,
                roleCode
        );
    }

    public long countActiveUsersByRoleExcluding(String roleCode, Long excludedUserId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT account.id)
                FROM casbin_rule rule
                JOIN sys_user account ON account.id::text = rule.v0
                WHERE rule.ptype = 'g'
                  AND rule.v1 = ?
                  AND account.deleted_flag = false
                  AND account.status = 'NORMAL'
                  AND account.id <> ?
                """,
                Long.class,
                roleCode,
                excludedUserId
        );
        return count == null ? 0L : count;
    }

    public void replaceRolePermissions(String roleCode, Collection<CasbinPolicy> permissions) {
        jdbcTemplate.update("DELETE FROM casbin_rule WHERE ptype = 'p' AND v0 = ?", roleCode);
        if (permissions != null && !permissions.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', ?, ?, ?)",
                    permissions.stream()
                            .map(policy -> new Object[]{roleCode, policy.resource(), policy.action()})
                            .toList()
            );
        }
        reloadAfterCommit();
    }

    public void replaceUserRoles(String subject, Collection<String> roleCodes) {
        jdbcTemplate.update("DELETE FROM casbin_rule WHERE ptype = 'g' AND v0 = ?", subject);
        if (roleCodes != null && !roleCodes.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO casbin_rule (ptype, v0, v1) VALUES ('g', ?, ?)",
                    roleCodes.stream().distinct().map(roleCode -> new Object[]{subject, roleCode}).toList()
            );
        }
        reloadAfterCommit();
    }

    public void renameRole(String oldRoleCode, String newRoleCode) {
        if (oldRoleCode.equals(newRoleCode)) {
            return;
        }
        jdbcTemplate.update("UPDATE casbin_rule SET v0 = ? WHERE ptype = 'p' AND v0 = ?", newRoleCode, oldRoleCode);
        jdbcTemplate.update("UPDATE casbin_rule SET v1 = ? WHERE ptype = 'g' AND v1 = ?", newRoleCode, oldRoleCode);
        jdbcTemplate.update("UPDATE casbin_rule SET v0 = ? WHERE ptype = 'g' AND v0 = ?", newRoleCode, oldRoleCode);
        reloadAfterCommit();
    }

    public void deleteRole(String roleCode) {
        jdbcTemplate.update(
                """
                DELETE FROM casbin_rule
                WHERE (ptype = 'p' AND v0 = ?)
                   OR (ptype = 'g' AND (v0 = ? OR v1 = ?))
                """,
                roleCode,
                roleCode,
                roleCode
        );
        reloadAfterCommit();
    }

    private void reloadAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            reloadPolicy();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reloadPolicy();
            }
        });
    }

    private void reloadPolicy() {
        try {
            enforcer.loadPolicy();
        } catch (RuntimeException ex) {
            log.error("Failed to reload jCasbin policy after database commit", ex);
            throw ex;
        }
    }
}
