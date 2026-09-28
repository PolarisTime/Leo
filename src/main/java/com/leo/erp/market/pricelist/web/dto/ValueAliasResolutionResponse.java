package com.leo.erp.market.pricelist.web.dto;

/**
 * 归一化预览响应(只读): 回答前端"这个写法会归一到什么"。
 *
 * @param value       归一前的原写法(trim 后)
 * @param lookupKey   实际用于查表的键(定尺为定尺口径归一后的写法, 其余等于 value)
 * @param targetValue 归一后的写法(映射命中 → 目标值; 未命中 → 现有归一化回退结果)
 * @param matched     是否命中映射行(false 表示走现有归一化回退/原样返回)
 */
public record ValueAliasResolutionResponse(
        String dimension,
        String value,
        String lookupKey,
        String targetValue,
        boolean matched
) {
}
