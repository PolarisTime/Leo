package com.leo.erp.common.retry;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RetryExecutor} 边界测试：重试白名单、有界次数、退避计算与配置钳制。
 */
class RetryExecutorTest {

    private static RetryExecutor executor(int maxAttempts, long initialBackoff, double multiplier, long maxBackoff) {
        RetryProperties settings = new RetryProperties();
        settings.setMaxAttempts(maxAttempts);
        settings.setInitialBackoffMillis(initialBackoff);
        settings.setMultiplier(multiplier);
        settings.setMaxBackoffMillis(maxBackoff);
        return RetryExecutor.from(settings);
    }

    /** 无退避执行器：测试只关心尝试次数，避免真实等待。 */
    private static RetryExecutor noBackoff(int maxAttempts) {
        return executor(maxAttempts, 0L, 2.0, 0L);
    }

    @Test
    void 首次成功时不重试() {
        AtomicInteger calls = new AtomicInteger();

        String result = noBackoff(3).execute("行情列表页", () -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void 瞬时失败后重试直至成功() {
        AtomicInteger calls = new AtomicInteger();

        String result = noBackoff(3).execute("行情列表页", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new TransientCallException("HTTP 503");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void 非瞬时异常不重试且原样抛出() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> work = () -> {
            calls.incrementAndGet();
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参数非法");
        };

        assertThatThrownBy(() -> noBackoff(3).execute("行情列表页", work))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数非法");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void 重试次数用尽后抛出瞬时异常并标注尝试次数() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> work = () -> {
            calls.incrementAndGet();
            throw new TransientCallException("请求超时");
        };

        assertThatThrownBy(() -> noBackoff(3).execute("行情列表页", work))
                .isInstanceOf(TransientCallException.class)
                .hasMessageContaining("请求超时")
                .hasMessageContaining("已尝试 3 次");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void 最大尝试次数为1时关闭重试() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> work = () -> {
            calls.incrementAndGet();
            throw new TransientCallException("HTTP 500");
        };

        assertThatThrownBy(() -> noBackoff(1).execute("行情列表页", work))
                .isInstanceOf(TransientCallException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void 尝试次数为0或负数时至少执行一次() {
        AtomicInteger calls = new AtomicInteger();

        String result = noBackoff(0).execute("行情列表页", () -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void 尝试次数超硬上限时被钳制() {
        RetryProperties settings = new RetryProperties();
        settings.setMaxAttempts(1000);

        RetryExecutor retry = RetryExecutor.from(settings);

        assertThat(retry.getMaxAttempts()).isEqualTo(RetryExecutor.MAX_ATTEMPTS_LIMIT);
    }

    @Test
    void 配置为空时使用默认值() {
        RetryExecutor retry = RetryExecutor.from(null);

        assertThat(retry.getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void 默认配置为3次尝试与1s起退避() {
        RetryProperties defaults = new RetryProperties();

        assertThat(defaults.getMaxAttempts()).isEqualTo(3);
        assertThat(defaults.getInitialBackoffMillis()).isEqualTo(1000L);
        assertThat(defaults.getMultiplier()).isEqualTo(2.0);
        assertThat(defaults.getMaxBackoffMillis()).isEqualTo(8000L);
    }

    @Test
    void 退避按倍数递增并封顶() {
        RetryExecutor retry = executor(5, 1000L, 2.0, 3000L);

        assertThat(retry.backoffMillis(1)).isEqualTo(1000L);
        assertThat(retry.backoffMillis(2)).isEqualTo(2000L);
        assertThat(retry.backoffMillis(3)).isEqualTo(3000L);
        assertThat(retry.backoffMillis(4)).isEqualTo(3000L);
    }

    @Test
    void 退避倍数为0或负数时退回默认倍数() {
        RetryExecutor retry = executor(5, 1000L, 0.0, 8000L);

        assertThat(retry.backoffMillis(1)).isEqualTo(1000L);
        assertThat(retry.backoffMillis(2)).isEqualTo(2000L);
    }

    @Test
    void 退避上限小于首次退避时以首次退避为准() {
        RetryExecutor retry = executor(5, 5000L, 2.0, 1000L);

        assertThat(retry.backoffMillis(1)).isEqualTo(5000L);
        assertThat(retry.backoffMillis(2)).isEqualTo(5000L);
    }

    @Test
    void 线程已中断时不再继续重试() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> work = () -> {
            calls.incrementAndGet();
            Thread.currentThread().interrupt();
            throw new TransientCallException("HTTP 503");
        };

        try {
            assertThatThrownBy(() -> noBackoff(3).execute("行情列表页", work))
                    .isInstanceOf(TransientCallException.class);
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            Thread.interrupted();
        }
    }
}
