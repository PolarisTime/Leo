package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KeyedLockRegistryTest {

    /** 同一键上的执行必须互斥, 任何时刻最多一个临界区。 */
    @Test
    void execute_serializesSameKey() throws InterruptedException {
        KeyedLockRegistry registry = new KeyedLockRegistry();
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        Runnable task = () -> {
            try {
                start.await();
                registry.execute(1L, () -> {
                    maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    concurrent.decrementAndGet();
                    return null;
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        Thread first = new Thread(task);
        Thread second = new Thread(task);
        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();

        assertThat(maxConcurrent.get()).isEqualTo(1);
    }

    /** 执行结束且无等待者时必须回收锁, 避免按业务键无限增长。 */
    @Test
    void execute_recyclesLockWhenIdle() throws ReflectiveOperationException {
        KeyedLockRegistry registry = new KeyedLockRegistry();

        registry.execute(42L, () -> "done");

        assertThat(locksOf(registry)).isEmpty();
    }

    /** 串行执行期间不得提前回收锁, 等待者必须复用同一把锁, 全部结束后再回收。 */
    @Test
    void execute_keepsLockWhileWaiterQueued() throws Exception {
        KeyedLockRegistry registry = new KeyedLockRegistry();
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> registry.execute(7L, () -> {
            acquired.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        holder.start();
        acquired.await();

        Thread waiter = new Thread(() -> {
            waiterStarted.countDown();
            registry.execute(7L, () -> null);
        });
        waiter.start();
        waiterStarted.await();
        Thread.sleep(30);

        assertThat(locksOf(registry).containsKey(7L)).isTrue();

        release.countDown();
        holder.join();
        waiter.join();

        assertThat(locksOf(registry)).isEmpty();
    }

    private Map<?, ?> locksOf(KeyedLockRegistry registry) throws ReflectiveOperationException {
        Field field = KeyedLockRegistry.class.getDeclaredField("locks");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(registry);
    }
}
