package com.leo.erp.common.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 有界重试执行器：只对 {@link TransientCallException}（瞬时失败信号）做有限次退避重试，
 * 其余异常一律原样抛出。
 *
 * <p><b>选型对齐：</b>与 {@link com.leo.erp.common.transaction.OptimisticLockRetryExecutor}
 * 保持同一风格——纯 Java 实现，不新增 {@code spring-retry} 依赖、不引入 AOP 切面，
 * 也不把「重试与否」建立在异常消息字符串匹配上：是否重试由异常类型白名单决定，
 * 单元测试可用一个返回固定行为的 {@link Supplier} 直接驱动。</p>
 *
 * <p><b>退避策略：</b>第 n 次失败后等待 {@code initialBackoffMillis × multiplier^(n-1)}，
 * 并封顶 {@code maxBackoffMillis}。退避既给对端（Mysteel/西本）留出恢复窗口，
 * 也顺带承担了重试之间的请求间隔，避免风控叠加。</p>
 *
 * <p><b>中断语义：</b>退避等待期间线程被中断时不再继续重试，恢复中断标记并抛出最后一次瞬时失败，
 * 由调用方决定如何上报；取数线程池因此不会被「重试 + 超时」无限拖住。</p>
 */
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    /** 尝试次数硬上限：防止误配成极大值后长期占住同步线程并放大对目标站点的压力。 */
    static final int MAX_ATTEMPTS_LIMIT = 10;
    private static final double DEFAULT_MULTIPLIER = 2.0;

    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final double multiplier;
    private final long maxBackoffMillis;

    RetryExecutor(int maxAttempts, long initialBackoffMillis, double multiplier, long maxBackoffMillis) {
        this.maxAttempts = maxAttempts;
        this.initialBackoffMillis = initialBackoffMillis;
        this.multiplier = multiplier;
        this.maxBackoffMillis = Math.max(maxBackoffMillis, initialBackoffMillis);
    }

    /** 由配置构造；{@code settings} 为空时退回 {@link RetryProperties} 默认值。 */
    public static RetryExecutor from(RetryProperties settings) {
        RetryProperties effective = settings == null ? new RetryProperties() : settings;
        int attempts = Math.min(Math.max(effective.getMaxAttempts(), 1), MAX_ATTEMPTS_LIMIT);
        long initialBackoff = Math.max(effective.getInitialBackoffMillis(), 0L);
        double factor = effective.getMultiplier() >= 1.0 ? effective.getMultiplier() : DEFAULT_MULTIPLIER;
        long maxBackoff = effective.getMaxBackoffMillis() > 0 ? effective.getMaxBackoffMillis() : initialBackoff;
        return new RetryExecutor(attempts, initialBackoff, factor, maxBackoff);
    }

    /** 最大尝试次数（含首次）。 */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * 执行取数逻辑，仅对瞬时失败重试。
     *
     * @param action 日志与错误消息用的动作名（如「行情列表页」「西本杭州报价」）
     * @param work   一次取数尝试
     * @return 取数结果
     * @throws TransientCallException 尝试次数用尽（或等待退避时被中断）后的最后一次瞬时失败
     */
    public <T> T execute(String action, Supplier<T> work) {
        String lastMessage = null;
        int tried = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            tried = attempt;
            try {
                T result = work.get();
                if (attempt > 1) {
                    log.info("{} 重试成功(第 {}/{} 次尝试)", action, attempt, maxAttempts);
                }
                return result;
            } catch (TransientCallException ex) {
                lastMessage = ex.getMessage();
                if (attempt >= maxAttempts) {
                    break;
                }
                long backoff = backoffMillis(attempt);
                log.warn("{} 第 {}/{} 次尝试失败, {}ms 后重试: {}",
                        action, attempt, maxAttempts, backoff, ex.getMessage());
                if (!sleepBeforeNextAttempt(backoff)) {
                    // 线程已被中断：放弃剩余重试，由调用方恢复中断语义。
                    break;
                }
            }
        }
        throw new TransientCallException(lastMessage + "(已尝试 " + tried + " 次)");
    }

    /** 第 {@code failedAttempt} 次失败（1 起）之后应等待的毫秒数，封顶 {@code maxBackoffMillis}。 */
    long backoffMillis(int failedAttempt) {
        if (initialBackoffMillis <= 0) {
            return 0L;
        }
        double raw = initialBackoffMillis * Math.pow(multiplier, Math.max(0, failedAttempt - 1));
        return raw >= maxBackoffMillis ? maxBackoffMillis : (long) raw;
    }

    /** @return true 表示可以继续下一次尝试；false 表示线程被中断，应放弃重试 */
    private boolean sleepBeforeNextAttempt(long millis) {
        if (millis <= 0) {
            return !Thread.currentThread().isInterrupted();
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
