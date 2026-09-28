package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.market.pricelist.domain.entity.ValueAlias;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import com.leo.erp.market.pricelist.repository.ValueAliasRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 某个维度的全部值映射(按维度批量取, 一次查询)。
 *
 * <p><b>缓存口径与仓库既有约定一致</b>: 复用 {@link CacheConfig#CACHE_OPTIONS} 命名缓存
 * (与商品类别/供应商/客户等选项缓存同一 TTL 与同一 Redis 缓存管理器, 不引入新的缓存层),
 * 键为 {@code leo:value-alias:<维度>}; 值映射的增删改在 {@link ValueAliasStore} 上以
 * {@code @CacheEvict(allEntries = true)} 失效(与 {@code ProjectService} 同口径),
 * 因此改完映射后字典/校验/推导立刻读不到旧映射。</p>
 *
 * <p>缓存不可用时由 {@code CacheConfig#errorHandler} 降级为直查数据库(与其它选项缓存一致)。</p>
 */
@Component
public class ValueAliasMappings {

    /** 值映射缓存键前缀(与 {@code CacheConfig.CACHE_KEY_PREFIX} 叠加, 便于排查)。 */
    public static final String CACHE_KEY_PREFIX = "leo:value-alias:";

    private final ValueAliasRepository repository;

    public ValueAliasMappings(ValueAliasRepository repository) {
        this.repository = repository;
    }

    /**
     * 该维度的全部未删除映射({@code 源值 → 目标值})。
     *
     * <p>同维度同源值由部分唯一索引保证至多一条, 这里仍按源值 {@code putIfAbsent} 收敛,
     * 使未执行迁移/历史脏数据下的结果保持确定。</p>
     *
     * @return 可读不可改的映射视图(调用方不得修改缓存值)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'" + CACHE_KEY_PREFIX + "' + #dimension.name()")
    public Map<String, String> load(ValueAliasDimension dimension) {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (ValueAlias alias : repository.findByDimensionAndDeletedFlagFalseOrderBySourceValueAscIdAsc(dimension)) {
            if (alias.getSourceValue() == null || alias.getTargetValue() == null) {
                continue;
            }
            mappings.putIfAbsent(alias.getSourceValue(), alias.getTargetValue());
        }
        return mappings;
    }
}
