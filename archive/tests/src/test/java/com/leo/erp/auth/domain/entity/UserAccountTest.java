package com.leo.erp.auth.domain.entity;

import com.leo.erp.auth.domain.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountTest {

    @Test
    void shouldCreateUserAccountWithDefaultValues() {
        UserAccount account = new UserAccount();

        assertThat(account.getTotpEnabled()).isFalse();
        assertThat(account.getRequireTotpSetup()).isFalse();
    }

    @Test
    void shouldSetAndGetAllFields() {
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setVersion(0L);
        account.setLoginName("admin");
        account.setPasswordHash("hashed_password");
        account.setUserName("管理员");
        account.setMobile("13800138000");
        account.setDepartmentId(100L);
        account.setDepartmentName("技术部");
        account.setDataScope("ALL");
        account.setPermissionSummary("sys:user:*");
        account.setLastLoginDate(LocalDateTime.of(2026, 1, 1, 10, 0));
        account.setStatus(UserStatus.NORMAL);
        account.setRemark("备注");
        account.setTotpSecret("JBSWY3DPEHPK3PXP");
        account.setTotpEnabled(true);
        account.setRequireTotpSetup(false);
        account.setPreferencesJson("{\"theme\":\"dark\"}");

        assertThat(account.getId()).isEqualTo(1L);
        assertThat(account.getVersion()).isEqualTo(0L);
        assertThat(account.getLoginName()).isEqualTo("admin");
        assertThat(account.getPasswordHash()).isEqualTo("hashed_password");
        assertThat(account.getUserName()).isEqualTo("管理员");
        assertThat(account.getMobile()).isEqualTo("13800138000");
        assertThat(account.getDepartmentId()).isEqualTo(100L);
        assertThat(account.getDepartmentName()).isEqualTo("技术部");
        assertThat(account.getDataScope()).isEqualTo("ALL");
        assertThat(account.getPermissionSummary()).isEqualTo("sys:user:*");
        assertThat(account.getLastLoginDate()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(account.getStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(account.getRemark()).isEqualTo("备注");
        assertThat(account.getTotpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(account.getTotpEnabled()).isTrue();
        assertThat(account.getRequireTotpSetup()).isFalse();
        assertThat(account.getPreferencesJson()).isEqualTo("{\"theme\":\"dark\"}");
    }

    @Test
    void shouldHandleNullValues() {
        UserAccount account = new UserAccount();
        account.setId(null);
        account.setLoginName(null);
        account.setUserName(null);
        account.setMobile(null);
        account.setDepartmentId(null);
        account.setDepartmentName(null);
        account.setDataScope(null);
        account.setPermissionSummary(null);
        account.setLastLoginDate(null);
        account.setStatus(null);
        account.setRemark(null);
        account.setTotpSecret(null);
        account.setPreferencesJson(null);

        assertThat(account.getId()).isNull();
        assertThat(account.getLoginName()).isNull();
        assertThat(account.getUserName()).isNull();
        assertThat(account.getMobile()).isNull();
        assertThat(account.getDepartmentId()).isNull();
        assertThat(account.getDepartmentName()).isNull();
        assertThat(account.getDataScope()).isNull();
        assertThat(account.getPermissionSummary()).isNull();
        assertThat(account.getLastLoginDate()).isNull();
        assertThat(account.getStatus()).isNull();
        assertThat(account.getRemark()).isNull();
        assertThat(account.getTotpSecret()).isNull();
        assertThat(account.getPreferencesJson()).isNull();
    }

    @Test
    void shouldInheritAuditableFields() {
        UserAccount account = new UserAccount();
        account.setCreatedBy(1L);
        account.setCreatedName("system");
        account.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        account.setUpdatedBy(2L);
        account.setUpdatedName("admin");
        account.setUpdatedAt(LocalDateTime.of(2026, 6, 1, 12, 0));
        account.setDeletedFlag(false);

        assertThat(account.getCreatedBy()).isEqualTo(1L);
        assertThat(account.getCreatedName()).isEqualTo("system");
        assertThat(account.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(account.getUpdatedBy()).isEqualTo(2L);
        assertThat(account.getUpdatedName()).isEqualTo("admin");
        assertThat(account.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 12, 0));
        assertThat(account.isDeletedFlag()).isFalse();
    }
}
