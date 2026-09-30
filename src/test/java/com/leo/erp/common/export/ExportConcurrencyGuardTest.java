package com.leo.erp.common.export;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 导出并发闸门测试（2026-09-30 压测报告 P2）。
 *
 * <p>闸门的职责是「保命」：导出额度耗尽时<strong>快速失败 429</strong>，
 * 而不是排队等额度——排队会把 Tomcat 线程与数据库连接一起耗尽，
 * 把「导出变慢」放大成「全站不可用」。因此核心断言是：</p>
 * <ol>
 *   <li>额度可用时透传执行并返回结果；</li>
 *   <li>额度耗尽（含 0 等待超时）时抛 {@code TOO_MANY_REQUESTS}，且<strong>不执行</strong>业务动作；</li>
 *   <li>业务动作无论正常还是异常都必须归还额度，否则一次失败会永久吃掉一个额度；</li>
 *   <li>等待期间被中断要抛 429 并恢复中断标记，不得静默吞掉。</li>
 * </ol>
 */
class ExportConcurrencyGuardTest {

    private static ExportConcurrencyGuard guard(int maxConcurrent, long acquireTimeoutMillis) {
        ExportConcurrencyProperties properties = new ExportConcurrencyProperties();
        properties.setMaxConcurrent(maxConcurrent);
        properties.setAcquireTimeoutMillis(acquireTimeoutMillis);
        return new ExportConcurrencyGuard(properties);
    }

    @Test
    void execute_runsActionAndReturnsItsResult() {
        ExportConcurrencyGuard guard = guard(2, 100L);

        String result = guard.execute("test", () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(guard.availablePermits()).isEqualTo(2);
    }

    /** 配置 0 或负数必须收敛为 1，否则闸门会变成「全部拒绝」。 */
    @Test
    void execute_withNonPositiveMaxConcurrent_stillAllowsOneAtATime() {
        ExportConcurrencyGuard guard = guard(0, 0L);

        assertThat(guard.execute("test", () -> "ok")).isEqualTo("ok");
        assertThat(guard.availablePermits()).isEqualTo(1);
    }

    @Test
    void execute_whenPermitsExhausted_failsFastWithoutRunningAction() {
        ExportConcurrencyGuard guard = guard(1, 0L);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger actionRuns = new AtomicInteger();

        Thread occupier = new Thread(() -> guard.execute("occupy", () -> {
            held.countDown();
            await(release);
            return null;
        }));
        occupier.start();
        await(held);

        try {
            assertThatThrownBy(() -> guard.execute("blocked", actionRuns::incrementAndGet))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
            // 拒绝时业务动作一次都不能跑：闸门必须在动作之前判定
            assertThat(actionRuns.get()).isZero();
        } finally {
            release.countDown();
            join(occupier);
        }
    }

    /** 超时等待同样快速失败：等待有上限，绝不允许无界排队。 */
    @Test
    void execute_whenWaitingForPermitTimesOut_failsFast() throws Exception {
        ExportConcurrencyGuard guard = guard(1, 50L);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread occupier = new Thread(() -> guard.execute("occupy", () -> {
            held.countDown();
            await(release);
            return null;
        }));
        occupier.start();
        await(held);

        try {
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> guard.execute("waiter", () -> "never"))
                    .isInstanceOf(BusinessException.class);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            // 50ms 超时 + 调度余量：绝不能是无界等待
            assertThat(elapsedMillis).isLessThan(5_000L);
        } finally {
            release.countDown();
            join(occupier);
        }
    }

    /** 额度必须在 finally 归还：动作抛异常若不归还，一次失败就永久吃掉一个额度。 */
    @Test
    void execute_whenActionThrows_releasesPermit() {
        ExportConcurrencyGuard guard = guard(1, 0L);

        assertThatCode(() -> guard.execute("boom", () -> {
            throw new IllegalStateException("导出失败");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(guard.availablePermits()).isEqualTo(1);
        assertThat(guard.execute("after-failure", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void execute_whenInterruptedWhileWaiting_failsWith429AndRestoresInterruptFlag() throws Exception {
        ExportConcurrencyGuard guard = guard(1, 60_000L);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);

        Thread occupier = new Thread(() -> guard.execute("occupy", () -> {
            held.countDown();
            await(release);
            return null;
        }));
        occupier.start();
        await(held);

        AtomicInteger actionRuns = new AtomicInteger();
        AtomicBoolean interruptRestored = new AtomicBoolean(false);
        Thread waiter = new Thread(() -> {
            waiterStarted.countDown();
            try {
                guard.execute("interrupted", actionRuns::incrementAndGet);
            } catch (BusinessException expected) {
                // 预期：429；guard 抛出时必须已恢复中断标记（吞掉会让上层永远感知不到取消）
                interruptRestored.set(Thread.currentThread().isInterrupted());
            }
        });
        waiter.start();
        await(waiterStarted);
        waiter.interrupt();
        join(waiter);

        try {
            assertThat(actionRuns.get()).isZero();
            assertThat(interruptRestored.get())
                    .as("中断标记必须在抛 429 之前恢复，否则调用方无法感知取消")
                    .isTrue();
        } finally {
            release.countDown();
            join(occupier);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待测试闩锁被中断", ex);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join(5_000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待测试线程结束被中断", ex);
        }
        assertThat(thread.isAlive()).isFalse();
    }
}
