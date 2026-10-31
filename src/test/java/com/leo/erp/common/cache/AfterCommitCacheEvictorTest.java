package com.leo.erp.common.cache;

import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.support.AfterCommitExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

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

    private void afterCommit() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(synchronization -> synchronization.afterCommit());
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }
}
