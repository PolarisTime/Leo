package com.leo.erp.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis 的双维度限流计数（2026-09-29 压测报告建议第 3 条的落点）。
 *
 * <h3>算法选型：Redis ZSET 滑动窗口（逐条记分），而非固定窗口 / 令牌桶 / 进程内计数</h3>
 * <ul>
 *   <li><b>固定窗口（INCR + EXPIRE）</b>：最省资源，但窗口交界处允许 2 倍额度突发
 *       （上一窗尾 + 下一窗头连打），且 Retry-After 只能粗略给到「窗口结束」；
 *       本任务明确要求覆盖「窗口交界」行为，固定窗口在此处语义最差，故不选。</li>
 *   <li><b>令牌桶</b>：平滑放行，但需要额外维护 refill 时间戳与余量，突发容量语义
 *       （桶深）对「读路径削峰」并不比滑动窗口更贴切；实现与测试面更大。</li>
 *   <li><b>进程内令牌桶/滑动窗口</b>：多实例部署时额度按实例数线性放大
 *       （3 个实例 = 3 倍额度），与「全局维度」目标直接矛盾，故不选。</li>
 *   <li><b>ZSET 滑动窗口</b>：每条请求以毫秒时间戳为 score 记入 ZSET，先裁剪窗外记录再计数。
 *       窗口交界处记录<b>逐条滑出</b>而非整窗清零，无 2 倍突发；
 *       Retry-After 可由最老一条记录精确推导（{@code oldest + window - now}）。
 *       代价是每键内存 O(额度)（默认全局 300、主体 60，且带 TTL 自动回收），可控。</li>
 * </ul>
 *
 * <h3>存储与多实例语义</h3>
 * <p>计数只存 Redis，单实例与多实例共用同一份计数：多实例部署时「全局维度」是<b>集群全局</b>语义
 * （所有实例共享 300/秒），用户/IP 维度跨实例同样累计——这正是限流想要的。
 * 各实例只是各自发起一次 EVAL，Redis 单线程执行天然互斥，实例之间不需要任何同步。
 * 注意：脚本同时操作两个键，若未来迁移到 Redis Cluster 需要给两个键加同一个 hash tag
 * （当前为单实例/主从部署，不存在该问题）。</p>
 *
 * <h3>时钟</h3>
 * <p>判定时刻 {@code now} 由调用方（过滤器）经 {@link RateLimitCheck} 传入（应用统一 {@code Clock}），
 * 作为 ARGV 传给脚本，裁剪、记账、Retry-After 推导全部基于这一个时间基准，不会混用多个时钟；
 * epoch millis 本身无时区概念。多实例时各实例时钟依赖 NTP 同步——偏差通常在毫秒级，
 * 相对 1 秒窗口可忽略；不使用 Redis {@code TIME} 是为了保持判定输入可控、可测。</p>
 *
 * <h3>原子性</h3>
 * <p>「裁剪 → 计数 → 准入 → 记账 → 刷新 TTL」全部在一个 Lua 脚本内完成，由 Redis 原子执行。
 * 这同时规避了 INCR 与 EXPIRE 分离的经典竞态（两条命令之间崩溃会留下永不过期的计数键）。
 * ZADD 使用「时间戳 + UUID」作为 member，保证同一毫秒内的并发请求各记一条、不互相覆盖。</p>
 *
 * <h3>fail-open</h3>
 * <p>Redis 不可用（连接异常、脚本异常、返回结构异常、模板缺失）一律返回
 * {@link RateLimitResult#unavailable()} 并记 WARN——限流器只负责削峰，
 * 绝不允许自己变成 5xx 来源（与认证链路 Redis 异常穿透成 500 的实测教训相反向约束）。</p>
 */
@Slf4j
@Component
public class RedisRateLimitStore implements RateLimitStore {

    /** 全局维度计数键。 */
    static final String GLOBAL_KEY = "leo:rate-limit:global";

    /** 主体维度计数键前缀，后接 {@code u:42} / {@code ip:10.0.0.1}。 */
    static final String SUBJECT_KEY_PREFIX = "leo:rate-limit:subject:";

    /** 脚本返回数组长度：{@code {globalAllowed, globalRetry, subjectAllowed, subjectRetry}}。 */
    private static final int REPLY_SIZE = 4;

    /** 主体维度重试毫秒在脚本返回数组中的下标。 */
    private static final int IDX_SUBJECT_RETRY = 3;

    /**
     * 双维度滑动窗口脚本。返回 4 元素数组：
     * {@code {globalAllowed, globalRetryMillis, subjectAllowed, subjectRetryMillis}}。
     *
     * <p>顺序约定：先全局后主体——全局超限时直接短路返回，不再操作主体键，
     * 过载时每次拒绝只需 1 次脚本内的最小操作集，也避免把每个用户的键都写脏。
     * 代价是「全局放行但主体超限」时全局键已记了一次数（被拒请求也占全局额度），
     * 这在削峰场景是保守方向（宁可多计、不可多放）。额度 {@code <=0} 的维度直接视为通过。</p>
     */
    private static final DefaultRedisScript<List> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[5])
            local function probe(key, limitArg, windowArg)
                local limit = tonumber(limitArg)
                local window = tonumber(windowArg)
                if limit == nil or limit <= 0 or window == nil or window <= 0 then
                    return {1, 0}
                end
                redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
                local count = redis.call('ZCARD', key)
                if count < limit then
                    redis.call('ZADD', key, now, ARGV[6])
                    redis.call('PEXPIRE', key, window)
                    return {1, 0}
                end
                redis.call('PEXPIRE', key, window)
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local retry = window
                if oldest[1] ~= nil then
                    retry = tonumber(oldest[2]) + window - now
                end
                if retry < 1 then
                    retry = 1
                end
                return {0, math.floor(retry)}
            end
            local g = probe(KEYS[1], ARGV[1], ARGV[2])
            if g[1] == 0 then
                return {0, g[2], 1, 0}
            end
            local s = probe(KEYS[2], ARGV[3], ARGV[4])
            return {1, 0, s[1], s[2]}
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitStore(@Nullable StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult tryAcquire(RateLimitCheck check) {
        if (check == null) {
            return RateLimitResult.unavailable();
        }
        // 双维度都关闭时不需要访问 Redis（过滤器侧已短路，这里再兜一层防御）。
        if (check.globalLimit() <= 0 && check.subjectLimit() <= 0) {
            return RateLimitResult.allowed();
        }
        if (redisTemplate == null) {
            log.warn("限流 Redis 模板缺失，fail-open 放行: subject={}", check.subject());
            return RateLimitResult.unavailable();
        }
        try {
            Instant now = check.now();
            List<?> raw = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    List.of(GLOBAL_KEY, SUBJECT_KEY_PREFIX + check.subject()),
                    String.valueOf(check.globalLimit()),
                    String.valueOf(check.globalWindow().toMillis()),
                    String.valueOf(check.subjectLimit()),
                    String.valueOf(check.subjectWindow().toMillis()),
                    String.valueOf(now.toEpochMilli()),
                    now.toEpochMilli() + ":" + UUID.randomUUID()
            );
            return decode(raw);
        } catch (RuntimeException ex) {
            // fail-open：Redis 抖动只允许降低限流强度，不允许把请求打成 5xx。
            log.warn("限流 Redis 访问失败，fail-open 放行: subject={}, reason={}",
                    check.subject(), ex.toString());
            return RateLimitResult.unavailable();
        }
    }

    private RateLimitResult decode(List<?> raw) {
        if (raw == null || raw.size() < REPLY_SIZE) {
            log.warn("限流脚本返回结构异常，fail-open 放行: result={}", raw);
            return RateLimitResult.unavailable();
        }
        long globalAllowed = asLong(raw.get(0));
        long globalRetry = asLong(raw.get(1));
        long subjectAllowed = asLong(raw.get(2));
        long subjectRetry = asLong(raw.get(IDX_SUBJECT_RETRY));
        if (globalAllowed == 1L && subjectAllowed == 1L) {
            return RateLimitResult.allowed();
        }
        if (globalAllowed != 1L) {
            return RateLimitResult.limited(RateLimitResult.Dimension.GLOBAL, globalRetry);
        }
        return RateLimitResult.limited(RateLimitResult.Dimension.SUBJECT, subjectRetry);
    }

    /** Redis 整数回复通常为 {@link Long}，防御性兼容字符串回复；解析失败按不可用处理（fail-open）。 */
    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text) {
            return Long.parseLong(text.toString());
        }
        throw new IllegalStateException("Unexpected rate limit script reply: " + value);
    }
}
