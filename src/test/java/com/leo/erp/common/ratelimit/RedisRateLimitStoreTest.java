package com.leo.erp.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Redis 限流计数存储测试：结果解析、fail-open、参数传递、窗口交界与并发递增。
 *
 * <p>「窗口交界 / 并发递增」两项通过<b>可控时钟 + 复刻 Lua 脚本语义的测试替身</b>驱动：
 * 存储层把 {@link RateLimitCheck#now()} 作为 ARGV 传给脚本，替身按与脚本完全相同的
 * 「裁剪 → 计数 → 准入 → 记账」规则计数，从而在单测里验证滑动窗口在交界时刻逐条滑出、
 * 并发下计数不超额度。真正的原子性由 Redis 单线程执行同一段 Lua 保证
 * （见 {@link RedisRateLimitStore} 类注释），此处验证的是 Java 侧每次判定只发起一次脚本调用、
 * 且参数（时间基准/窗口/额度/键名）传递正确。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisRateLimitStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-29T08:00:00Z");

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimitStore store;

    @BeforeEach
    void setUp() {
        store = new RedisRateLimitStore(redisTemplate);
    }

    // ---- 脚本返回解析 ------------------------------------------------------------------

    @Test
    void scriptReplyAllAllowed_returnsAllowed() {
        stubScriptReply(List.of(1L, 0L, 1L, 0L));

        RateLimitResult result = store.tryAcquire(check(60, 1, 300, 1, NOW));

        assertThat(result.status()).isEqualTo(RateLimitResult.Status.ALLOWED);
        assertThat(result.dimension()).isEqualTo(RateLimitResult.Dimension.NONE);
    }

    @Test
    void scriptReplyGlobalDenied_returnsLimitedGlobalDimension() {
        stubScriptReply(List.of(0L, 1500L, 1L, 0L));

        RateLimitResult result = store.tryAcquire(check(60, 1, 300, 1, NOW));

        assertThat(result.status()).isEqualTo(RateLimitResult.Status.LIMITED);
        assertThat(result.dimension()).isEqualTo(RateLimitResult.Dimension.GLOBAL);
        assertThat(result.retryAfterMillis()).isEqualTo(1500L);
    }

    @Test
    void scriptReplySubjectDenied_returnsLimitedSubjectDimension() {
        stubScriptReply(List.of(1L, 0L, 0L, 800L));

        RateLimitResult result = store.tryAcquire(check(60, 1, 300, 1, NOW));

        assertThat(result.status()).isEqualTo(RateLimitResult.Status.LIMITED);
        assertThat(result.dimension()).isEqualTo(RateLimitResult.Dimension.SUBJECT);
        assertThat(result.retryAfterMillis()).isEqualTo(800L);
    }

    @Test
    void malformedScriptReply_failsOpenAsUnavailable() {
        stubScriptReply(List.of(1L));

        RateLimitResult result = store.tryAcquire(check(60, 1, 300, 1, NOW));

        assertThat(result.status()).isEqualTo(RateLimitResult.Status.UNAVAILABLE);
    }

    @Test
    void nullScriptReply_failsOpenAsUnavailable() {
        stubScriptReply(null);

        RateLimitResult result = store.tryAcquire(check(60, 1, 300, 1, NOW));

        assertThat(result.status()).isEqualTo(RateLimitResult.Status.UNAVAILABLE);
    }

    // ---- fail-open：Redis 故障只降级限流强度，绝不向上传播 ----------------------------

    @Test
    void redisThrows_returnsUnavailableInsteadOfPropagating() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("Redis connection failure"));

        RateLimitResult result = store.tryAcquire(check(60, 1, 300, 1, NOW));

        assertThat(result.status()).isEqualTo(RateLimitResult.Status.UNAVAILABLE);
    }

    @Test
    void nullRedisTemplate_failsOpenAndBothDimensionsDisabledSkipsRedis() {
        RedisRateLimitStore storeWithoutRedis = new RedisRateLimitStore(null);

        // 模板缺失 → 不可用（fail-open 由过滤器放行）
        RateLimitResult unavailable = storeWithoutRedis.tryAcquire(check(60, 1, 300, 1, NOW));
        assertThat(unavailable.status()).isEqualTo(RateLimitResult.Status.UNAVAILABLE);

        // 双额度均 <=0 → 无需 Redis 也直接放行（额度 0/负数的语义是关闭维度，不是全拒绝）
        RateLimitResult allowed = storeWithoutRedis.tryAcquire(check(0, 1, -1, 1, NOW));
        assertThat(allowed.status()).isEqualTo(RateLimitResult.Status.ALLOWED);
    }

    // ---- 参数传递（keys / ARGV / 可控时钟）--------------------------------------------

    @Test
    void passesKeysLimitsWindowsAndClockToScript() {
        AtomicReference<List<String>> keysRef = new AtomicReference<>();
        AtomicReference<List<String>> argvRef = new AtomicReference<>();
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenAnswer(invocation -> {
            keysRef.set(castKeys(invocation.getArgument(1)));
            argvRef.set(argvOf(invocation));
            return List.of(1L, 0L, 1L, 0L);
        });
        Instant now = Instant.parse("2026-09-30T01:02:03.004Z");

        store.tryAcquire(check(42, 5, 777, 7, now));

        assertThat(keysRef.get())
                .containsExactly("leo:rate-limit:global", "leo:rate-limit:subject:u:42");
        List<String> argv = argvRef.get();
        assertThat(argv).hasSize(6);
        assertThat(argv.get(0)).isEqualTo("777");   // 全局额度
        assertThat(argv.get(1)).isEqualTo("7000");  // 全局窗口毫秒
        assertThat(argv.get(2)).isEqualTo("42");    // 主体额度
        assertThat(argv.get(3)).isEqualTo("5000");  // 主体窗口毫秒
        assertThat(argv.get(4)).isEqualTo(String.valueOf(now.toEpochMilli())); // 唯一时间基准
        assertThat(argv.get(5)).startsWith(now.toEpochMilli() + ":");          // 时间戳 + UUID member
    }

    // ---- 窗口交界（可控时钟 + 脚本语义替身）-------------------------------------------

    @Test
    void windowBoundary_entriesSlideOutOneByOneAtExactBoundary() {
        SlidingWindowScriptFake fake = stubScriptFake();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-29T08:00:00.000Z"));

        // 额度 3 / 1 秒窗口（全局维度关闭，只观察主体维度）
        for (int i = 0; i < 3; i++) {
            assertThat(store.tryAcquire(subjectLimitCheck(3, clock.instant())).status())
                    .isEqualTo(RateLimitResult.Status.ALLOWED);
        }
        // 第 4 次超限：Retry-After = 最老记录滑出窗口的剩余时间（整窗口）
        RateLimitResult denied = store.tryAcquire(subjectLimitCheck(3, clock.instant()));
        assertThat(denied.status()).isEqualTo(RateLimitResult.Status.LIMITED);
        assertThat(denied.dimension()).isEqualTo(RateLimitResult.Dimension.SUBJECT);
        assertThat(denied.retryAfterMillis()).isEqualTo(1000L);

        // 窗口交界前 1ms：仍在窗内，继续拒绝，剩余 1ms
        clock.advance(Duration.ofMillis(999));
        RateLimitResult almostBoundary = store.tryAcquire(subjectLimitCheck(3, clock.instant()));
        assertThat(almostBoundary.status()).isEqualTo(RateLimitResult.Status.LIMITED);
        assertThat(almostBoundary.retryAfterMillis()).isEqualTo(1L);

        // 恰好到达交界：最早记录按「score <= now - window」被裁剪（与脚本 ZREMRANGEBYSCORE 边界一致），
        // 记录逐条滑出后额度恢复，而不是「整窗清零再满额放行」
        clock.advance(Duration.ofMillis(1));
        assertThat(store.tryAcquire(subjectLimitCheck(3, clock.instant())).status())
                .isEqualTo(RateLimitResult.Status.ALLOWED);
        // 旧的 3 条已全部滑出，只剩本次准入的 1 条
        assertThat(fake.totalEntries()).isEqualTo(1);
    }

    // ---- 并发递增（可控时钟 + 脚本语义替身）-------------------------------------------

    @Test
    void concurrentAcquire_neverExceedsQuota() throws Exception {
        stubScriptFake();
        int limit = 25;
        int threadCount = 8;
        int attemptsPerThread = 10;
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger unavailable = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = new ThreadPoolExecutor(
                threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        RateLimitResult result = store.tryAcquire(subjectLimitCheck(limit, NOW));
                        if (result.status() == RateLimitResult.Status.ALLOWED) {
                            allowed.incrementAndGet();
                        } else if (result.status() == RateLimitResult.Status.UNAVAILABLE) {
                            unavailable.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // 80 次并发请求恰好放行 limit 次：不多放（限流失效）也不少放（误伤额度）
        assertThat(allowed.get()).isEqualTo(limit);
        assertThat(unavailable.get()).isZero();
    }

    // ---- helpers ----------------------------------------------------------------------

    private RateLimitCheck check(int subjectLimit, long subjectWindowSeconds,
                                 int globalLimit, long globalWindowSeconds, Instant now) {
        return new RateLimitCheck(
                "u:42",
                subjectLimit,
                Duration.ofSeconds(subjectWindowSeconds),
                globalLimit,
                Duration.ofSeconds(globalWindowSeconds),
                now);
    }

    /** 仅启用主体维度（额度可指定 / 1 秒窗口）的判定输入，用于观察滑动窗口行为。 */
    private RateLimitCheck subjectLimitCheck(int subjectLimit, Instant now) {
        return new RateLimitCheck("u:42", subjectLimit, Duration.ofSeconds(1), 0, Duration.ofSeconds(1), now);
    }

    private void stubScriptReply(List<?> reply) {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(reply);
    }

    /** 用「复刻 Lua 语义」的替身接管脚本执行，返回替身以便断言。 */
    private SlidingWindowScriptFake stubScriptFake() {
        SlidingWindowScriptFake fake = new SlidingWindowScriptFake();
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenAnswer(invocation ->
                fake.evaluate(castKeys(invocation.getArgument(1)), argvOf(invocation)));
        return fake;
    }

    @SuppressWarnings("unchecked")
    private List<String> castKeys(Object value) {
        return (List<String>) value;
    }

    /** 兼容 Mockito 是否展开 varargs 的两种形态，取出脚本的 6 个 ARGV。 */
    private List<String> argvOf(InvocationOnMock invocation) {
        Object[] arguments = invocation.getArguments();
        Object[] values;
        if (arguments.length == 3 && arguments[2] instanceof Object[] packed) {
            values = packed;
        } else {
            values = new Object[arguments.length - 2];
            System.arraycopy(arguments, 2, values, 0, values.length);
        }
        List<String> argv = new ArrayList<>(values.length);
        for (Object value : values) {
            argv.add(String.valueOf(value));
        }
        return argv;
    }

    /**
     * {@code ACQUIRE_SCRIPT} 的 Java 侧语义镜像：裁剪（score {@code <= now - window}）→
     * 计数 → 准入 → 记账，全程 synchronized 模拟 Redis 单线程脚本执行的原子性。
     * 同一毫秒的并发请求各占一条记录（与 ZADD 不同 member 互不覆盖一致）。
     */
    private static final class SlidingWindowScriptFake {

        private final Map<String, List<Long>> window = new HashMap<>();

        synchronized List<Long> evaluate(List<String> keys, List<String> argv) {
            long globalLimit = Long.parseLong(argv.get(0));
            long globalWindow = Long.parseLong(argv.get(1));
            long subjectLimit = Long.parseLong(argv.get(2));
            long subjectWindow = Long.parseLong(argv.get(3));
            long now = Long.parseLong(argv.get(4));
            long[] global = probe(keys.get(0), globalLimit, globalWindow, now);
            if (global[0] == 0L) {
                return List.of(0L, global[1], 1L, 0L);
            }
            long[] subject = probe(keys.get(1), subjectLimit, subjectWindow, now);
            return List.of(1L, 0L, subject[0], subject[1]);
        }

        synchronized int totalEntries() {
            return window.values().stream().mapToInt(List::size).sum();
        }

        private long[] probe(String key, long limit, long windowMillis, long now) {
            if (limit <= 0 || windowMillis <= 0) {
                return new long[]{1L, 0L};
            }
            List<Long> scores = window.computeIfAbsent(key, ignored -> new ArrayList<>());
            scores.removeIf(score -> score <= now - windowMillis);
            if (scores.size() < limit) {
                scores.add(now);
                return new long[]{1L, 0L};
            }
            long retry = windowMillis;
            if (!scores.isEmpty()) {
                retry = Collections.min(scores) + windowMillis - now;
            }
            if (retry < 1) {
                retry = 1;
            }
            return new long[]{0L, retry};
        }
    }

    /** 测试用可控时钟：窗口交界行为由它驱动，而不是 sleep。 */
    private static final class MutableClock extends java.time.Clock {

        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
