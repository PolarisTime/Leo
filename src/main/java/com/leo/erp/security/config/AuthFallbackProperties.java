package com.leo.erp.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 认证链路在 Redis 不可用时的降级窗口配置。
 *
 * <p>背景（2026-09-30 压测报告 P0 结论）：认证链路直接读 Redis
 * （{@code AuthenticatedUserCacheService#getActivePrincipal}），异常
 * {@code RedisConnectionFailureException} 不在 {@code JwtAuthenticationFilter}
 * 捕获的 {@code JwtException | IllegalArgumentException} 之内，于是直接变成
 * **500 Internal Server Error**。三重对照实测：同一个 token 在正常实例上 200，
 * 在同一时刻的故障实例上 500，而坏签名 token 仍是 401（说明签名校验已通过）、
 * {@code ExpiredJwtException} 计数为 0。也就是说 Redis 抖动会把**所有已登录请求**打成 500。</p>
 *
 * <p>降级策略（两层，均可在下面关闭）：</p>
 * <ol>
 *   <li><b>有界降级窗口（默认关闭）</b>：开启后，每次成功读取都会把「主体快照」放进进程内缓存；
 *       Redis 读取失败时，如果快照仍在 {@code ttlSeconds} 内，就用它继续放行请求，
 *       同时本窗口内跳过 Redis 侧的吊销校验。这是一次明确的取舍——
 *       **窗口内已被吊销/已登出的会话最多还能再用 {@code ttlSeconds} 秒**。
 *       默认关闭：宁可 503，也不在未确认的前提下放宽吊销语义。</li>
 *   <li><b>窗口外返回 503</b>：拿不到快照时不再 500，而是
 *       {@code 503 + code 5030}「依赖服务暂不可用」，让监控能区分依赖故障与代码缺陷，
 *       客户端也能据此重试而不是报障。</li>
 * </ol>
 */
@ConfigurationProperties(prefix = "leo.security.auth-fallback")
public class AuthFallbackProperties {

    private static final long DEFAULT_TTL_SECONDS = 5;
    private static final int DEFAULT_MAX_ENTRIES = 10_000;

    /**
     * 是否启用降级窗口。
     *
     * <p><b>默认关闭（fail-closed）</b>：Redis 读取失败一律 503。
     * 打开后，窗口内将跳过 Redis 侧的吊销校验、并允许使用进程内快照放行，
     * 即「已被吊销/已登出的会话最多还能再用 ttl 秒」——这是安全取舍，
     * 需要在明确接受该延迟的前提下才打开（例如 Redis 抖动导致大面积 503 不可接受时）。</p>
     */
    private boolean enabled = false;

    /** 降级窗口时长（秒）；0 表示不启用窗口（等价于关闭）。 */
    private long ttlSeconds = DEFAULT_TTL_SECONDS;

    /** 进程内快照的最大条数，防止无界增长。 */
    private int maxEntries = DEFAULT_MAX_ENTRIES;

    public boolean isEnabled() {
        return enabled && ttlSeconds > 0 && maxEntries > 0;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public Duration ttl() {
        return Duration.ofSeconds(Math.max(0, ttlSeconds));
    }
}
