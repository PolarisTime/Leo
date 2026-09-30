package com.leo.erp.common.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 导出并发闸门配置（见 {@link ExportConcurrencyGuard} 的取舍说明）。
 */
@ConfigurationProperties(prefix = "leo.export.concurrency")
public class ExportConcurrencyProperties {

    private static final int DEFAULT_MAX_CONCURRENT = 4;
    private static final long DEFAULT_ACQUIRE_TIMEOUT_MILLIS = 500L;

    /** 同时进行的导出任务上限。 */
    private int maxConcurrent = DEFAULT_MAX_CONCURRENT;

    /** 等待并发额度的时间（毫秒）；超时即返回 429。 */
    private long acquireTimeoutMillis = DEFAULT_ACQUIRE_TIMEOUT_MILLIS;

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public long getAcquireTimeoutMillis() {
        return acquireTimeoutMillis;
    }

    public void setAcquireTimeoutMillis(long acquireTimeoutMillis) {
        this.acquireTimeoutMillis = acquireTimeoutMillis;
    }
}
