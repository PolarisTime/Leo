package com.leo.erp.common.ratelimit;

/**
 * 一次限流判定的结果。
 *
 * <p>{@link Status#UNAVAILABLE} 表示限流后端（Redis）不可用——调用方必须按 <b>fail-open</b>
 * 放行该请求：限流器是「保命」措施，它自身绝不能成为 5xx 的来源
 * （本项目实测教训：认证链路 Redis 异常曾穿透成 500）。</p>
 */
public record RateLimitResult(Status status, Dimension dimension, long retryAfterMillis) {

    public enum Status {
        /** 额度内，放行。 */
        ALLOWED,
        /** 超限，应返回 429 + Retry-After。 */
        LIMITED,
        /** 限流后端不可用，fail-open 放行。 */
        UNAVAILABLE
    }

    /** 超限命中的维度。 */
    public enum Dimension {
        NONE,
        /** 全局维度超限。 */
        GLOBAL,
        /** 主体（用户/IP）维度超限。 */
        SUBJECT
    }

    public static RateLimitResult allowed() {
        return new RateLimitResult(Status.ALLOWED, Dimension.NONE, 0L);
    }

    /**
     * @param dimension       超限维度
     * @param retryAfterMillis 距离最老一条在窗记录滑出窗口的剩余毫秒数（用于 Retry-After）
     */
    public static RateLimitResult limited(Dimension dimension, long retryAfterMillis) {
        return new RateLimitResult(Status.LIMITED, dimension, Math.max(0L, retryAfterMillis));
    }

    public static RateLimitResult unavailable() {
        return new RateLimitResult(Status.UNAVAILABLE, Dimension.NONE, 0L);
    }
}
