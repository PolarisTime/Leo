package com.leo.erp.master.valuealias.service;

import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.master.api.ValueAliasDimension;
import com.leo.erp.master.api.ValueAliasQuery;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 值映射查询门面：供业务侧按维度取「源写法 → 目标写法」映射。
 *
 * <p>与 {@link ValueAliasService} 分开的原因有两个：一是行情匹配等热路径每次匹配都要读映射，
 * 必须走缓存；二是 {@code @Cacheable} 在同类内部调用不生效，必须经由独立 Bean 调用才能命中缓存。
 * 缓存复用 {@code options} 区（下拉类短缓存），写操作会精确失效对应维度。</p>
 *
 * <p>实现 {@link ValueAliasQuery}：跨模块调用方（market）只依赖 {@code master.api} 里的接口，
 * 不再依赖本包内部类型，避免 Spring Modulith 的「依赖未暴露类型」违规。</p>
 */
@Component
public class ValueAliasLookup implements ValueAliasQuery {

    private final ValueAliasService valueAliasService;

    public ValueAliasLookup(ValueAliasService valueAliasService) {
        this.valueAliasService = valueAliasService;
    }

    /**
     * 取某维度的归一映射；无数据时返回空 Map，由调用方决定是否回退到内置默认值。
     *
     * <p>返回空 Map 与返回 null 语义不同：空表示「表里确实没有该维度的规则」，调用方据此
     * 回退到 yml / 代码里的默认规则，从而保证「表未初始化」时行为与改造前一致。</p>
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'value-alias:' + #dimension.name()")
    public Map<String, String> mapping(ValueAliasDimension dimension) {
        return valueAliasService.mappingOf(dimension);
    }
}
