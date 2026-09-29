package com.leo.erp.master.material.service;

import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.support.AfterCommitExecutor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 物料字典（材质/品牌选项）的缓存 key 与失效入口。
 *
 * <p>{@code materialGrades()} / {@code materialBrands()} 是全表 {@code SELECT DISTINCT}，
 * 结果只在物料主数据变化时改变，因此缓存于 {@link CacheConfig#CACHE_OPTIONS}。</p>
 *
 * <p><strong>为什么把失效集中在这里：</strong>物料有 3 个写入路径
 * （{@link MaterialService} 的手工增改删、{@code MaterialImportProcessor} 的导入、
 * {@link MaterialBatchRollbackService} 的批次回滚）。若各自维护失效逻辑，
 * 漏掉任何一条都会让前端下拉框长期显示已不存在的材质/品牌。
 * 这里提供唯一的 {@link #evictAll()} 入口，并在<em>事务提交后</em>执行：
 * 若在提交前失效，并发请求可能以「未提交的旧数据」回填缓存，从而读到陈旧字典。</p>
 */
@Component
public class MaterialDictionaryCache {

    /** 材质（品名）选项缓存 key。 */
    public static final String GRADES_CACHE_KEY = "leo:material:grades";

    /** 品牌选项缓存 key。 */
    public static final String BRANDS_CACHE_KEY = "leo:material:brands";

    private final CacheManager cacheManager;
    private final AfterCommitExecutor afterCommitExecutor;

    public MaterialDictionaryCache(CacheManager cacheManager, AfterCommitExecutor afterCommitExecutor) {
        this.cacheManager = cacheManager;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    /** 物料主数据发生任何变更后调用；事务提交后生效。 */
    public void evictAll() {
        afterCommitExecutor.run(() -> {
            evict(GRADES_CACHE_KEY);
            evict(BRANDS_CACHE_KEY);
        });
    }

    private void evict(String key) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_OPTIONS);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
