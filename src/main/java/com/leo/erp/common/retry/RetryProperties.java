package com.leo.erp.common.retry;

/**
 * 有界重试配置：最大尝试次数与退避参数。
 *
 * <p>以嵌套对象的形式挂在各取数数据源的配置下（如 {@code leo.market.steel-quote.retry}、
 * {@code leo.market.steelx-quote.retry}），由 Spring Boot 松散绑定；默认值与
 * {@link RetryExecutor} 的默认行为一致：最多 3 次尝试、1s 起退避、倍数 2、单次退避上限 8s。</p>
 */
public class RetryProperties {

    /** 最大尝试次数（含首次执行）；小于等于 1 表示关闭重试。 */
    private int maxAttempts = 3;
    /** 首次失败后的退避时长（毫秒）；0 表示不退避直接重试。 */
    private long initialBackoffMillis = 1000L;
    /** 退避倍数：下次退避 = 上次退避 × 倍数。 */
    private double multiplier = 2.0;
    /** 单次退避上限（毫秒），避免最坏情况下长时间占住同步线程。 */
    private long maxBackoffMillis = 8000L;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialBackoffMillis() {
        return initialBackoffMillis;
    }

    public void setInitialBackoffMillis(long initialBackoffMillis) {
        this.initialBackoffMillis = initialBackoffMillis;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public long getMaxBackoffMillis() {
        return maxBackoffMillis;
    }

    public void setMaxBackoffMillis(long maxBackoffMillis) {
        this.maxBackoffMillis = maxBackoffMillis;
    }
}
