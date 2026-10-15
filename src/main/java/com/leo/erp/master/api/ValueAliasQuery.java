package com.leo.erp.master.api;

import java.util.Map;

/**
 * 值映射跨模块只读查询（行情匹配使用）。
 *
 * <p>与 {@code MaterialQuery} 同一约定：接口在 {@code master.api} 声明，实现留在 master 内部
 * （{@code ValueAliasLookup}，带缓存）。调用方只依赖本接口，不得依赖
 * {@code master.valuealias.service} 的内部类型——那会被 Spring Modulith 判为
 * 「依赖未暴露类型」。</p>
 */
public interface ValueAliasQuery {

    /**
     * 取某维度的「源写法 → 目标写法」归一映射；无数据时返回空 Map。
     *
     * <p>返回空 Map 与返回 null 语义不同：空表示「表里确实没有该维度的规则」，调用方据此
     * 回退到 yml / 代码里的默认规则，从而保证「表未初始化」时行为与改造前一致。</p>
     */
    Map<String, String> mapping(ValueAliasDimension dimension);
}
