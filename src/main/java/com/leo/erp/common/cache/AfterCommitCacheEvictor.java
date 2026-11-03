package com.leo.erp.common.cache;

import com.leo.erp.common.support.AfterCommitExecutor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 主数据缓存失效的唯一出口：<b>事务提交后</b>再失效，避免"提交前失效 → 并发读用未提交的旧值
 * 回填缓存 → 整个 TTL 窗口读到已删/已改的旧数据"。
 *
 * <p>背景：{@code @CacheEvict} 挂在 {@code @Transactional} 方法上时，缓存拦截器在事务拦截器内层，
 * 语义是"方法返回即失效"，此时事务尚未提交；并发读可能把旧值重新写回缓存。物料字典
 * （{@code MaterialDictionaryCache}）早已改为 after-commit 口径，其余主数据仍用 {@code @CacheEvict}，
 * 两套口径同时存在——本类把两套收敛成一套：所有主数据写路径统一调用
 * {@link #evict(String, Object)} / {@link #evictAll(String)}。</p>
 *
 * <p><b>双重延后陷阱（2026-11-03 修复）</b>：缓存管理器配置了 {@code transactionAware()}，
 * 每个 Cache 都是 {@link TransactionAwareCacheDecorator} —— 它自己就会把 put/evict 延后到
 * afterCommit。此前本类在先注册一个 afterCommit 回调、再在回调里 {@code cache.evict(...)}：
 * 回调执行时事务同步仍处于 active 状态，装饰器于是又注册了一个"本次提交之后"的同步——
 * 而这次提交的 afterCommit 已经在跑，新同步永远不会被调用，<b>失效被静默丢弃</b>。
 * 线上表现：保存供应商经营品牌后，报价页的供应商下拉仍是旧品牌（{@code /suppliers/options}
 * 读到陈旧缓存），手动删 Redis key 才恢复。</p>
 *
 * <p>现在的口径：装饰器存在时直接调用它（它已经是 after-commit 语义，只延后一次）；
 * 缓存不是装饰器时（单测的 {@code ConcurrentMapCacheManager} 等）才由本类负责延后。
 * 无论哪条路径，都不会再出现"延后两次、一次都不生效"。</p>
 */
@Component
public class AfterCommitCacheEvictor {

    private final CacheManager cacheManager;
    private final AfterCommitExecutor afterCommitExecutor;

    public AfterCommitCacheEvictor(CacheManager cacheManager, AfterCommitExecutor afterCommitExecutor) {
        this.cacheManager = cacheManager;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    /** 事务提交后失效 {@code cacheName} 下的单个 key；无事务时立即失效。 */
    public void evict(String cacheName, Object key) {
        run(cacheName, cache -> cache.evict(key));
    }

    /** 事务提交后清空整个 {@code cacheName} region（原 {@code @CacheEvict(allEntries = true)} 语义）。 */
    public void evictAll(String cacheName) {
        run(cacheName, Cache::clear);
    }

    private void run(String cacheName, Consumer<Cache> action) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        if (cache instanceof TransactionAwareCacheDecorator) {
            // 装饰器已经把动作排到 afterCommit；这里再延后一次就会永远不执行（见类注释）。
            action.accept(cache);
            return;
        }
        afterCommitExecutor.run(() -> action.accept(cache));
    }
}
