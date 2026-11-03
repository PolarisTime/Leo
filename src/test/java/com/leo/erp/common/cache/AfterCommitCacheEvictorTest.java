package com.leo.erp.common.cache;

import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.support.AfterCommitExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存失效口径：有事务时必须在<b>提交后</b>才失效，无事务时立即失效。
 *
 * <p>回归背景：{@code @CacheEvict} 在事务提交前执行，会让并发读用未提交的旧值回填缓存，
 * 之后整个 TTL 窗口都读到已删/已改的旧数据。本用例用 {@link TransactionSynchronizationManager}
 * 把"提交前不得失效 / 提交后才失效"钉死。</p>
 */
class AfterCommitCacheEvictorTest {

    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final AfterCommitCacheEvictor evictor =
            new AfterCommitCacheEvictor(cacheManager, new AfterCommitExecutor());

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void evict_withoutTransaction_evictsImmediately() {
        when(cacheManager.getCache(CacheConfig.CACHE_OPTIONS)).thenReturn(cache);

        evictor.evict(CacheConfig.CACHE_OPTIONS, "leo:customer:all");

        verify(cache).evict("leo:customer:all");
    }

    @Test
    void evict_withActiveTransaction_waitsForCommit() {
        when(cacheManager.getCache(CacheConfig.CACHE_OPTIONS)).thenReturn(cache);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        evictor.evict(CacheConfig.CACHE_OPTIONS, "leo:customer:all");

        // 提交前：绝不能失效，否则并发读会把未提交的旧值写回缓存
        verify(cache, never()).evict("leo:customer:all");

        afterCommit();

        verify(cache).evict("leo:customer:all");
    }

    @Test
    void evictAll_withActiveTransaction_clearsOnlyAfterCommit() {
        when(cacheManager.getCache(CacheConfig.CACHE_PROJECT_OPTIONS)).thenReturn(cache);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        evictor.evictAll(CacheConfig.CACHE_PROJECT_OPTIONS);

        verify(cache, never()).clear();

        afterCommit();

        verify(cache).clear();
    }

    @Test
    void evict_missingCacheRegionIsNoOp() {
        when(cacheManager.getCache("missing")).thenReturn(null);

        evictor.evict("missing", "key");
        evictor.evictAll("missing");
    }

    /**
     * 双重延后回归：缓存管理器配了 {@code transactionAware()}，真实 Cache 是
     * {@link TransactionAwareCacheDecorator}（它自己就会延后到 afterCommit）。
     *
     * <p>修复前：本类先注册 afterCommit 回调，回调里再调 {@code cache.evict(...)}，装饰器看到事务同步仍
     * active，又注册了一次"提交之后"的同步——那次提交的 afterCommit 已经在跑，新同步永远不会执行，
     * 失效被静默丢弃。线上表现就是保存供应商经营品牌后 {@code /suppliers/options} 仍返回旧品牌。</p>
     *
     * <p>这里用真实的 {@link ConcurrentMapCache} + 真实装饰器复现：提交走完后 key 必须真的消失。</p>
     */
    @Test
    void evict_throughTransactionAwareDecorator_actuallyRemovesEntry() {
        Cache target = new ConcurrentMapCache(CacheConfig.CACHE_OPTIONS);
        target.put("leo:supplier:all", List.of("旧品牌"));
        Cache decorated = new TransactionAwareCacheDecorator(target);
        when(cacheManager.getCache(CacheConfig.CACHE_OPTIONS)).thenReturn(decorated);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        evictor.evict(CacheConfig.CACHE_OPTIONS, "leo:supplier:all");

        // 提交前不得失效
        assertThat(target.get("leo:supplier:all")).isNotNull();

        afterCommit();

        assertThat(target.get("leo:supplier:all"))
                .as("提交后缓存条目必须真的被删除；否则报价页会一直读到旧品牌")
                .isNull();
    }

    @Test
    void evict_throughTransactionAwareDecorator_withoutTransaction_removesImmediately() {
        Cache target = new ConcurrentMapCache(CacheConfig.CACHE_OPTIONS);
        target.put("leo:supplier:all", List.of("旧品牌"));
        when(cacheManager.getCache(CacheConfig.CACHE_OPTIONS))
                .thenReturn(new TransactionAwareCacheDecorator(target));

        evictor.evict(CacheConfig.CACHE_OPTIONS, "leo:supplier:all");

        assertThat(target.get("leo:supplier:all")).isNull();
    }

    private void afterCommit() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(synchronization -> synchronization.afterCommit());
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }
}
