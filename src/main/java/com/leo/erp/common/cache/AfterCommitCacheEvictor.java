package com.leo.erp.common.cache;

import com.leo.erp.common.support.AfterCommitExecutor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

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
 * <p>无事务上下文（如单测、只读命令）时立即执行，保证行为可预期。</p>
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
        afterCommitExecutor.run(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        });
    }

    /** 事务提交后清空整个 {@code cacheName} region（原 {@code @CacheEvict(allEntries = true)} 语义）。 */
    public void evictAll(String cacheName) {
        afterCommitExecutor.run(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
