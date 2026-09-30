package com.leo.erp.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 读路径限流配置（{@code leo.rate-limit.*}）。
 *
 * <p><b>默认关闭</b>（{@code enabled=false}）：关闭状态下 {@link RateLimitFilter} 第一行即放行，
 * 不访问 Redis、不改变任何请求的处理路径，行为与未引入限流时完全一致。
 * 这是 2026-09-29 压测报告建议第 3 条的可选保护措施，dev/测试环境保持关闭，
 * 生产环境按需通过 {@code LEO_RATE_LIMIT_*} 环境变量打开。</p>
 *
 * <p><b>默认额度的取值依据</b>（保守值，打开即安全）：</p>
 * <ul>
 *   <li>{@code user-limit=60/1s}：交互页面一次打开会并发发几十个 GET，
 *       60/秒 远高于真人操作的峰值，但足以拦住脚本刷数、前端死循环重试这类失控客户端；</li>
 *   <li>{@code global-limit=300/1s}：实测单实例混合吞吐上限约 850 req/s（2026-09-29 报告），
 *       全局读限额取其约 1/3，让读请求在逼近饱和前先被削峰，给写路径与框架开销留出余量，
 *       避免「排队等待 → 延迟无界增长」；</li>
 *   <li>窗口取 1 秒：报告诉求是「过载时<b>快速</b>返回 429」，短窗口能在一秒量级内感知过载，
 *       而不是等分钟级窗口积压完才拒绝。</li>
 * </ul>
 *
 * <p><b>边界语义</b>：额度 {@code <=0} 表示该维度不限流（把 0 配成「全部拒绝」过于危险，
 * 误配置会直接变成拒绝服务）；窗口 {@code <=0} 回退为 1 秒。</p>
 */
@ConfigurationProperties(prefix = "leo.rate-limit")
public class RateLimitProperties {

    /** 总开关，默认关闭。 */
    private boolean enabled = false;

    /** 是否把写方法（POST/PUT/PATCH/DELETE）也纳入限流；默认 false，仅限 GET 读路径。 */
    private boolean limitWriteMethods = false;

    /** 用户维度额度（已认证按用户 ID，匿名按客户端 IP）：每窗口允许的请求数；<=0 表示关闭该维度。 */
    private int userLimit = 60;

    /** 用户维度窗口时长（秒）；<=0 回退为 1 秒。 */
    private long userWindowSeconds = 1L;

    /** 全局额度（所有实例共享 Redis 计数）：每窗口允许的请求数；<=0 表示关闭该维度。 */
    private int globalLimit = 300;

    /** 全局窗口时长（秒）；<=0 回退为 1 秒。 */
    private long globalWindowSeconds = 1L;

    /**
     * 额外豁免路径（Ant 通配风格，逗号分隔）。内置豁免（health / actuator / 登录）不受本配置影响，
     * 详见 {@link RateLimitFilter} 的内置豁免清单。
     */
    private String excludePaths = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLimitWriteMethods() {
        return limitWriteMethods;
    }

    public void setLimitWriteMethods(boolean limitWriteMethods) {
        this.limitWriteMethods = limitWriteMethods;
    }

    public int getUserLimit() {
        return userLimit;
    }

    public void setUserLimit(int userLimit) {
        this.userLimit = userLimit;
    }

    public long getUserWindowSeconds() {
        return userWindowSeconds;
    }

    public void setUserWindowSeconds(long userWindowSeconds) {
        this.userWindowSeconds = userWindowSeconds;
    }

    public int getGlobalLimit() {
        return globalLimit;
    }

    public void setGlobalLimit(int globalLimit) {
        this.globalLimit = globalLimit;
    }

    public long getGlobalWindowSeconds() {
        return globalWindowSeconds;
    }

    public void setGlobalWindowSeconds(long globalWindowSeconds) {
        this.globalWindowSeconds = globalWindowSeconds;
    }

    public String getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(String excludePaths) {
        this.excludePaths = excludePaths;
    }

    /** 用户维度窗口；{@code <=0} 回退为 1 秒。 */
    public Duration userWindow() {
        return Duration.ofSeconds(Math.max(1L, userWindowSeconds));
    }

    /** 全局维度窗口；{@code <=0} 回退为 1 秒。 */
    public Duration globalWindow() {
        return Duration.ofSeconds(Math.max(1L, globalWindowSeconds));
    }
}
