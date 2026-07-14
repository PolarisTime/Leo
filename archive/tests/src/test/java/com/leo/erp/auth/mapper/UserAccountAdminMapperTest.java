package com.leo.erp.auth.mapper;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountAdminMapperTest {

    private final UserAccountAdminMapper mapper = new UserAccountAdminMapperImpl();

    @Test
    void shouldMapUserAccountToResponse() {
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setLoginName("admin");
        account.setUserName("管理员");
        account.setMobile("13800138000");
        account.setDepartmentId(10L);
        account.setDepartmentName("技术部");
        account.setDataScope("全部");
        account.setStatus(UserStatus.NORMAL);
        account.setRemark("系统管理员");
        account.setLastLoginDate(LocalDateTime.of(2024, 1, 1, 10, 0));
        account.setTotpEnabled(true);

        UserAccountAdminResponse response = mapper.toResponse(account);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.loginName()).isEqualTo("admin");
        assertThat(response.userName()).isEqualTo("管理员");
        assertThat(response.mobile()).isEqualTo("13800138000");
        assertThat(response.departmentId()).isEqualTo(10L);
        assertThat(response.departmentName()).isEqualTo("技术部");
        assertThat(response.dataScope()).isEqualTo("全部");
        assertThat(response.status()).isEqualTo("正常");
        assertThat(response.remark()).isEqualTo("系统管理员");
        assertThat(response.lastLoginDate()).isEqualTo(LocalDateTime.of(2024, 1, 1, 10, 0));
        assertThat(response.totpEnabled()).isTrue();
    }

    @Test
    void shouldMapDisabledStatus() {
        UserAccount account = new UserAccount();
        account.setStatus(UserStatus.DISABLED);

        UserAccountAdminResponse response = mapper.toResponse(account);

        assertThat(response.status()).isEqualTo("禁用");
    }

    @Test
    void shouldMapNullStatusToNormal() {
        UserAccount account = new UserAccount();
        account.setStatus(null);

        UserAccountAdminResponse response = mapper.toResponse(account);

        assertThat(response.status()).isEqualTo("正常");
    }

    @Test
    void shouldIgnoreRoleNamesAndRoleIds() {
        UserAccount account = new UserAccount();
        account.setId(1L);

        UserAccountAdminResponse response = mapper.toResponse(account);

        assertThat(response.roleNames()).isNull();
        assertThat(response.roleIds()).isNull();
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
