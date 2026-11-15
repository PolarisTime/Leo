package com.leo.erp.common.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 导出产物缓存配置（见 {@link InMemoryExportArtifactStore}）。
 *
 * <p>默认值按「一次导出在几分钟内被下载一次」的使用方式设定：
 * TTL 30 分钟、最多 32 份、总字节上限 64 MiB。超过容量时按最近最少使用顺序淘汰，
 * 单份产物超过总字节上限时直接拒绝创建（422），避免把大文件缓存成堆内存炸弹。</p>
 *
 * <p>可用 {@code leo.export.artifact.*} 覆盖；因为 {@code @ConfigurationPropertiesScan}
 * 会扫描本类，无需额外 {@code @Component}。</p>
 */
@ConfigurationProperties(prefix = "leo.export.artifact")
public class ExportArtifactProperties {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final int DEFAULT_MAX_ENTRIES = 32;
    private static final long DEFAULT_MAX_TOTAL_BYTES = 64L * 1024L * 1024L;

    /** 产物可回读窗口；过期后 GET 返回 404。 */
    private Duration ttl = DEFAULT_TTL;

    /** 最多保留的产物份数（LRU）。 */
    private int maxEntries = DEFAULT_MAX_ENTRIES;

    /** 所有产物字节总和上限；超出后按 LRU 淘汰。 */
    private long maxTotalBytes = DEFAULT_MAX_TOTAL_BYTES;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public long getMaxTotalBytes() {
        return maxTotalBytes;
    }

    public void setMaxTotalBytes(long maxTotalBytes) {
        this.maxTotalBytes = maxTotalBytes;
    }
}
