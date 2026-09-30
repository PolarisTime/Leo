package com.leo.erp.sales.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 销售订单写路径的乐观锁服务端重试配置（键前缀 {@code leo.sales-order.retry}），
 * 由 {@link com.leo.erp.common.transaction.OptimisticLockRetryExecutor} 消费。
 *
 * <p>背景：{@code PUT /api/v2.0/sales-orders/{id}} 等整体替换接口在 10 路并发写同一单据时，
 * {@code SalesOrder} 实体的 {@code @Version} 乐观锁让约 90% 请求落到 409，数据正确但失败责任全在客户端；
 * 本配置让服务端在冲突后有界重试（重放整段写逻辑），把可自动收敛的冲突在服务端消化掉。
 */
@ConfigurationProperties(prefix = "leo.sales-order.retry")
public class SalesOrderRetryProperties {

    /** 单次请求的最大尝试次数（含首次执行）；小于等于 1 表示禁用服务端重试，冲突直接按现状返回 409。 */
    private int maxAttempts = 3;

    /** 相邻两次尝试之间的退避毫秒数，避免并发方在同一时刻反复撞同一版本号。 */
    private long backoffMillis = 10L;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBackoffMillis() {
        return backoffMillis;
    }

    public void setBackoffMillis(long backoffMillis) {
        this.backoffMillis = backoffMillis;
    }
}
