package com.leo.erp.master.material.service;

import com.leo.erp.common.cache.AfterCommitCacheEvictor;
import com.leo.erp.common.config.CacheConfig;
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
 * 这里提供唯一的 {@link #evictAll()} 入口，并统一走
 * {@link AfterCommitCacheEvictor}（事务提交后失效）：若在提交前失效，并发请求可能以
 * 「未提交的旧数据」回填缓存，从而读到陈旧字典。</p>
 */
@Component
public class MaterialDictionaryCache {

    /** 材质（品名）选项缓存 key。 */
    public static final String GRADES_CACHE_KEY = "leo:material:grades";

    /** 品牌选项缓存 key。 */
    public static final String BRANDS_CACHE_KEY = "leo:material:brands";

    private final AfterCommitCacheEvictor cacheEvictor;

    public MaterialDictionaryCache(AfterCommitCacheEvictor cacheEvictor) {
        this.cacheEvictor = cacheEvictor;
    }

    /** 物料主数据发生任何变更后调用；事务提交后生效（与其它主数据共用同一 after-commit 口径）。 */
    public void evictAll() {
        cacheEvictor.evict(CacheConfig.CACHE_OPTIONS, GRADES_CACHE_KEY);
        cacheEvictor.evict(CacheConfig.CACHE_OPTIONS, BRANDS_CACHE_KEY);
    }
}
