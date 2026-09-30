package com.leo.erp.common.ratelimit;

/**
 * 限流计数存储。
 *
 * <p><b>契约</b>：实现类<b>不得抛出异常</b>——任何后端故障必须折叠成
 * {@link RateLimitResult#unavailable()}（fail-open），由过滤器放行并记 WARN 日志。
 * {@link RateLimitFilter} 另有一层 try/catch 双保险，确保限流器不会把请求打成 5xx。</p>
 */
public interface RateLimitStore {

    /**
     * 在「全局 + 主体」双维度上尝试为本次请求记一次数；任一维度超限即整体拒绝。
     *
     * @param check 判定输入（额度/窗口/时刻/主体键）
     * @return 判定结果，绝不为 null，绝不抛异常
     */
    RateLimitResult tryAcquire(RateLimitCheck check);
}
