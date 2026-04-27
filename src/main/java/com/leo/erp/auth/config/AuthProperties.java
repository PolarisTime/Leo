package com.leo.erp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "leo.auth")
public class AuthProperties {

    private final Bootstrap bootstrap = new Bootstrap();
    private final User user = new User();
    private final LoginProtection loginProtection = new LoginProtection();

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public User getUser() {
        return user;
    }

    public LoginProtection getLoginProtection() {
        return loginProtection;
    }

    public static class Bootstrap {

        private boolean enabled;
        private String loginName;
        private String password;
        private String userName;
        private String mobile;
        private String dataScope;
        private String remark;
        private String roleCode;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLoginName() {
            return loginName;
        }

        public void setLoginName(String loginName) {
            this.loginName = loginName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getMobile() {
            return mobile;
        }

        public void setMobile(String mobile) {
            this.mobile = mobile;
        }

        public String getDataScope() {
            return dataScope;
        }

        public void setDataScope(String dataScope) {
            this.dataScope = dataScope;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public String getRoleCode() {
            return roleCode;
        }

        public void setRoleCode(String roleCode) {
            this.roleCode = roleCode;
        }
    }

    public static class User {

        private String defaultPassword;

        public String getDefaultPassword() {
            return defaultPassword;
        }

        public void setDefaultPassword(String defaultPassword) {
            this.defaultPassword = defaultPassword;
        }
    }

    public static class LoginProtection {

        private boolean enabled = true;
        private int maxFailures = 5;
        private long failureWindowSeconds = 900;
        private long lockDurationSeconds = 900;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public long getFailureWindowSeconds() {
            return failureWindowSeconds;
        }

        public void setFailureWindowSeconds(long failureWindowSeconds) {
            this.failureWindowSeconds = failureWindowSeconds;
        }

        public long getLockDurationSeconds() {
            return lockDurationSeconds;
        }

        public void setLockDurationSeconds(long lockDurationSeconds) {
            this.lockDurationSeconds = lockDurationSeconds;
        }
    }
}
