package com.leo.erp.system.operationlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 操作日志采集开关。
 *
 * <p>与 {@code leo.observability.operation-log}(分区保留策略)分开: 本类只决定「哪些会话类请求
 * 写入操作日志」, 与分区/保留期无关。</p>
 */
@ConfigurationProperties(prefix = "leo.operation-log")
public class OperationLogProperties {

    /**
     * 是否把 {@code /auth/refresh} 写入操作日志。
     *
     * <p>默认关闭: 刷新令牌是维持会话的技术动作, 同一用户一个会话期内会重复数百次
     * (实测 /auth/refresh 813 次刷屏), 不含业务审计价值, 会把真正的业务操作淹没;
     * 需要排查令牌刷新异常时可临时置为 {@code true}。</p>
     */
    private boolean logAuthRefresh = false;

    public boolean isLogAuthRefresh() {
        return logAuthRefresh;
    }

    public void setLogAuthRefresh(boolean logAuthRefresh) {
        this.logAuthRefresh = logAuthRefresh;
    }
}
