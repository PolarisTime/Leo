package com.leo.erp.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPropertiesTest {

    @Test
    void shouldReturnNestedBootstrapProperties() {
        AuthProperties props = new AuthProperties();
        AuthProperties.Bootstrap bootstrap = props.getBootstrap();

        assertThat(bootstrap).isNotNull();
        assertThat(bootstrap.isEnabled()).isFalse();
        assertThat(bootstrap.getLoginName()).isNull();
        assertThat(bootstrap.getPassword()).isNull();
        assertThat(bootstrap.getUserName()).isNull();
        assertThat(bootstrap.getMobile()).isNull();
        assertThat(bootstrap.getDataScope()).isNull();
        assertThat(bootstrap.getRemark()).isNull();
        assertThat(bootstrap.getRoleCode()).isNull();
    }

    @Test
    void shouldSetBootstrapProperties() {
        AuthProperties props = new AuthProperties();
        AuthProperties.Bootstrap bootstrap = props.getBootstrap();

        bootstrap.setEnabled(true);
        bootstrap.setLoginName("admin");
        bootstrap.setPassword("secret");
        bootstrap.setUserName("管理员");
        bootstrap.setMobile("13800138000");
        bootstrap.setDataScope("全部");
        bootstrap.setRemark("初始化管理员");
        bootstrap.setRoleCode("ADMIN");

        assertThat(bootstrap.isEnabled()).isTrue();
        assertThat(bootstrap.getLoginName()).isEqualTo("admin");
        assertThat(bootstrap.getPassword()).isEqualTo("secret");
        assertThat(bootstrap.getUserName()).isEqualTo("管理员");
        assertThat(bootstrap.getMobile()).isEqualTo("13800138000");
        assertThat(bootstrap.getDataScope()).isEqualTo("全部");
        assertThat(bootstrap.getRemark()).isEqualTo("初始化管理员");
        assertThat(bootstrap.getRoleCode()).isEqualTo("ADMIN");
    }

    @Test
    void shouldReturnNestedUserProperties() {
        AuthProperties props = new AuthProperties();
        AuthProperties.User user = props.getUser();

        assertThat(user).isNotNull();
        assertThat(user.getDefaultPassword()).isNull();
    }

    @Test
    void shouldSetUserProperties() {
        AuthProperties props = new AuthProperties();
        AuthProperties.User user = props.getUser();

        user.setDefaultPassword("123456");
        assertThat(user.getDefaultPassword()).isEqualTo("123456");
    }

    @Test
    void shouldReturnNestedLoginProtectionPropertiesWithDefaults() {
        AuthProperties props = new AuthProperties();
        AuthProperties.LoginProtection loginProtection = props.getLoginProtection();

        assertThat(loginProtection).isNotNull();
        assertThat(loginProtection.isEnabled()).isTrue();
        assertThat(loginProtection.getMaxFailures()).isEqualTo(5);
        assertThat(loginProtection.getFailureWindowSeconds()).isEqualTo(900);
        assertThat(loginProtection.getLockDurationSeconds()).isEqualTo(900);
    }

    @Test
    void shouldSetLoginProtectionProperties() {
        AuthProperties props = new AuthProperties();
        AuthProperties.LoginProtection loginProtection = props.getLoginProtection();

        loginProtection.setEnabled(false);
        loginProtection.setMaxFailures(3);
        loginProtection.setFailureWindowSeconds(600);
        loginProtection.setLockDurationSeconds(1800);

        assertThat(loginProtection.isEnabled()).isFalse();
        assertThat(loginProtection.getMaxFailures()).isEqualTo(3);
        assertThat(loginProtection.getFailureWindowSeconds()).isEqualTo(600);
        assertThat(loginProtection.getLockDurationSeconds()).isEqualTo(1800);
    }

    @Test
    void shouldReturnNestedRefreshTokenPropertiesWithDefaults() {
        AuthProperties props = new AuthProperties();
        AuthProperties.RefreshToken refreshToken = props.getRefreshToken();

        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken.isRotationEnabled()).isTrue();
        assertThat(refreshToken.getReuseGraceSeconds()).isEqualTo(30);
    }

    @Test
    void shouldSetRefreshTokenProperties() {
        AuthProperties props = new AuthProperties();
        AuthProperties.RefreshToken refreshToken = props.getRefreshToken();

        refreshToken.setRotationEnabled(false);
        refreshToken.setReuseGraceSeconds(60);

        assertThat(refreshToken.isRotationEnabled()).isFalse();
        assertThat(refreshToken.getReuseGraceSeconds()).isEqualTo(60);
    }
}
