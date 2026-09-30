package com.leo.erp.auth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 会话被顶掉（并发登录超出上限）的留痕与告警。
 *
 * <p>背景（2026-09-30 压测报告 P1 结论）：会话上限原先不但是硬编码的 3，
 * 而且吊销是**完全静默**的——只在 {@code auth_refresh_token} 表里留下一行
 * {@code revoke_reason=CONCURRENT_LIMIT}。后果有两个：</p>
 * <ul>
 *   <li>客户端只看到「会话已失效」，无法区分「自己被登出」与「被其它登录顶掉」；</li>
 *   <li>压测期间采集器与用例各自登录同一账号，互相吊销会话，
 *       表现成大面积 401（实测 99.3% 请求失败），而业务日志里没有任何线索，
 *       一度被误读成「接口故障」。会话数上限因此成了一个隐形的故障放大器。</li>
 * </ul>
 *
 * <p>本类只做两件小事：打印结构化 WARN 日志、累加 {@code leo_auth_session_evicted_total} 指标。
 * 指标缺失（未配置 MeterRegistry）时退化为只打日志，不影响主流程。</p>
 */
@Component
public class SessionEvictionReporter {

    private static final Logger log = LoggerFactory.getLogger(SessionEvictionReporter.class);

    private final Counter evictedCounter;

    public SessionEvictionReporter(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        this.evictedCounter = registry == null
                ? null
                : Counter.builder("leo.auth.session.evicted")
                        .description("因同一账号会话数超出上限而被吊销的会话数")
                        .register(registry);
    }

    /**
     * 记录一次会话吊销。
     *
     * @param userId    账号 ID
     * @param loginName 登录名（便于运维直接定位是哪个账号被顶）
     * @param revoked   本次吊销的会话数
     * @param maxSessions 生效的会话上限（便于区分「配置过小」与「账号异常」）
     */
    public void reportConcurrentLimitRevocation(Long userId, String loginName, int revoked, int maxSessions) {
        if (revoked <= 0) {
            return;
        }
        log.warn("会话数超过上限，吊销最旧会话 userId={} loginName={} revoked={} maxSessions={} reason=CONCURRENT_LIMIT；"
                        + "被吊销会话的 access token 会立即失效（401），客户端无法区分被顶掉与主动登出。"
                        + "服务账号/自动化账号请联系运维加入 leo.auth.session.exempt-login-names 白名单",
                userId, loginName, revoked, maxSessions);
        if (evictedCounter != null) {
            evictedCounter.increment(revoked);
        }
    }

    /** 账号命中豁免白名单时的留痕（DEBUG，避免正常运行刷日志）。 */
    public void reportExempt(String loginName, int activeSessions) {
        if (log.isDebugEnabled()) {
            log.debug("账号命中会话上限豁免白名单，不吊销会话 loginName={} activeSessions={}", loginName, activeSessions);
        }
    }
}
