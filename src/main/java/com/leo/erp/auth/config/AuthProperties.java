package com.leo.erp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "leo.auth")
public class AuthProperties {

    private final LoginProtection loginProtection = new LoginProtection();
    private final RefreshToken refreshToken = new RefreshToken();

    public LoginProtection getLoginProtection() {
        return loginProtection;
    }

    public RefreshToken getRefreshToken() {
        return refreshToken;
    }

    public static class LoginProtection {

        private static final long DEFAULT_FAILURE_WINDOW_SECONDS = 900;
        private static final long DEFAULT_LOCK_DURATION_SECONDS = 900;

        private boolean enabled = true;
        private int maxFailures = 5;
        private long failureWindowSeconds = DEFAULT_FAILURE_WINDOW_SECONDS;
        private long lockDurationSeconds = DEFAULT_LOCK_DURATION_SECONDS;

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

    public static class RefreshToken {

        private static final long DEFAULT_REUSE_GRACE_SECONDS = 30;

        private boolean rotationEnabled = true;
        private long reuseGraceSeconds = DEFAULT_REUSE_GRACE_SECONDS;

        public boolean isRotationEnabled() {
            return rotationEnabled;
        }

        public void setRotationEnabled(boolean rotationEnabled) {
            this.rotationEnabled = rotationEnabled;
        }

        public long getReuseGraceSeconds() {
            return reuseGraceSeconds;
        }

        public void setReuseGraceSeconds(long reuseGraceSeconds) {
            this.reuseGraceSeconds = reuseGraceSeconds;
        }
    }
}
