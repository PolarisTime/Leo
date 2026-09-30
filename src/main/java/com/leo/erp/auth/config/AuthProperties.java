package com.leo.erp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "leo.auth")
public class AuthProperties {

    private final LoginProtection loginProtection = new LoginProtection();
    private final RefreshToken refreshToken = new RefreshToken();
    private final Session session = new Session();
    private final LoginAudit loginAudit = new LoginAudit();

    public LoginProtection getLoginProtection() {
        return loginProtection;
    }

    public RefreshToken getRefreshToken() {
        return refreshToken;
    }

    public Session getSession() {
        return session;
    }

    public LoginAudit getLoginAudit() {
        return loginAudit;
    }

    /**
     * 登录会话相关配置。
     *
     * <p>背景（2026-09-30 压测报告）：同账号会话上限原先硬编码为 3，
     * 第 4 次登录会吊销并拉黑最旧会话，其 access token 立即 401，且**没有任何日志**。
     * 这既让运维无法调节，也让「被顶掉」与「自己登出」无法区分；
     * 服务账号（监控、集成、压测）与人工登录共用账号时更会互相踢掉。</p>
     */
    public static class Session {

        private static final int DEFAULT_MAX_REFRESH_TOKENS = 3;

        /** 同一账号允许并存的 refresh token 数；<=0 表示不限制。 */
        private int maxRefreshTokens = DEFAULT_MAX_REFRESH_TOKENS;

        /**
         * 豁免会话上限的账号白名单，支持「精确登录名」与「前缀*」两种写法；
         * 前缀写法要求星号至少对应一个字符（`svc_*` 不匹配 `svc_` 本身）。
         *
         * <p>用途：服务账号、监控账号、集成账号与自动化压测账号，
         * 避免它们与人工登录互相顶掉。</p>
         */
        private List<String> exemptLoginNames = new ArrayList<>();

        public int getMaxRefreshTokens() {
            return maxRefreshTokens;
        }

        public void setMaxRefreshTokens(int maxRefreshTokens) {
            this.maxRefreshTokens = maxRefreshTokens;
        }

        public List<String> getExemptLoginNames() {
            return exemptLoginNames;
        }

        public void setExemptLoginNames(List<String> exemptLoginNames) {
            this.exemptLoginNames = exemptLoginNames == null ? new ArrayList<>() : exemptLoginNames;
        }

        /** 判断账号是否豁免会话上限。 */
        public boolean isExempt(String loginName) {
            if (loginName == null || exemptLoginNames == null || exemptLoginNames.isEmpty()) {
                return false;
            }
            String candidate = loginName.trim();
            for (String pattern : exemptLoginNames) {
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                String trimmed = pattern.trim();
                if (trimmed.endsWith("*")) {
                    String prefix = trimmed.substring(0, trimmed.length() - 1);
                    // 两条严格化规则，避免「配置看起来加了、实际放行了不该放行的账号」：
                    // 1) 星号必须至少对应一个字符：`svc_*` 不放行名字恰为 `svc_` 的账号；
                    // 2) 不允许空前缀：只写 `*` 时忽略该条，防止一个手滑的星号豁免全部账号。
                    if (!prefix.isEmpty() && prefix.length() < candidate.length() && candidate.startsWith(prefix)) {
                        return true;
                    }
                } else if (trimmed.equals(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 登录审计写入配置。
     *
     * <p>背景：{@code LoginService} 原先每次登录都 {@code user.setLastLoginDate(...)}，
     * 而 {@code sys_user} 带 {@code @Version} 乐观锁且冲突不重试；
     * 实测同一账号 20 并发登录仅 5% 成功（92% 因乐观锁返回 409），
     * 并进一步放大成全站雪崩（连接池获取超时 7,902 次）。</p>
     */
    public static class LoginAudit {

        private static final long DEFAULT_WRITE_INTERVAL_SECONDS = 60;

        /**
         * 相邻两次「最后登录时间」落库的最小间隔（秒）；<=0 表示每次登录都写。
         *
         * <p>该字段是审计用途，不需要精确到每一次请求；节流后并发登录不再争抢同一行。</p>
         */
        private long lastLoginWriteIntervalSeconds = DEFAULT_WRITE_INTERVAL_SECONDS;

        public long getLastLoginWriteIntervalSeconds() {
            return lastLoginWriteIntervalSeconds;
        }

        public void setLastLoginWriteIntervalSeconds(long lastLoginWriteIntervalSeconds) {
            this.lastLoginWriteIntervalSeconds = lastLoginWriteIntervalSeconds;
        }
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
