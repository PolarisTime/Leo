package com.leo.erp.common.support;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 按业务键在进程内串行化、且不持有不被回收锁的可回收锁注册表。
 * <p>
 * 相比长期持有 {@code ConcurrentHashMap<Long, ReentrantLock>} 的实现, 本类在同一键
 * 没有持有者也没有等待者时会把锁从注册表移除, 避免按单据/项目 id 无限膨胀导致内存泄漏。
 * <p>
 * 引用计数与注册表的增删都通过 {@link ConcurrentHashMap#compute} 完成, 对同一键是原子的,
 * 因此不会出现"最后一个持有者移除锁, 而新线程仍拿到已脱离注册表的旧锁"从而并发失效的窗口。
 * 不同键的锁相互独立, 仅在哈希落桶碰撞时有轻微争用, 串行语义不降低。
 */
public final class KeyedLockRegistry {

    private final ConcurrentHashMap<Object, LockRef> locks = new ConcurrentHashMap<>();

    /**
     * 以 {@code key} 为粒度串行执行 {@code action}; 执行结束且无等待者时回收该键的锁。
     */
    public <T> T execute(Object key, Supplier<T> action) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");
        LockRef ref = retain(key);
        ref.lock.lock();
        try {
            return action.get();
        } finally {
            try {
                ref.lock.unlock();
            } finally {
                release(key, ref);
            }
        }
    }

    private LockRef retain(Object key) {
        return locks.compute(key, (ignored, existing) -> {
            if (existing == null) {
                return new LockRef();
            }
            existing.refs.incrementAndGet();
            return existing;
        });
    }

    private void release(Object key, LockRef ref) {
        locks.compute(key, (ignored, existing) -> {
            if (existing != ref) {
                return existing;
            }
            return ref.refs.decrementAndGet() == 0 ? null : ref;
        });
    }

    private static final class LockRef {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger refs = new AtomicInteger(1);
    }
}
