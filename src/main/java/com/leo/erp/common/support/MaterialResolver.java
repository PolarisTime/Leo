package com.leo.erp.common.support;

/**
 * 请求级商品解析器: 在同一请求的明细循环内复用一次商品目录快照。
 * <p>由 {@link TradeItemMaterialSupport#prepareResolver()} 创建, 校验语义与
 * {@link TradeItemMaterialSupport#resolveMaterial(Long, String, int)} 一致。
 */
public interface MaterialResolver {

    /**
     * 解析商品快照。
     *
     * @param materialId   商品内部标识; 非空时按 ID 解析并校验编码一致
     * @param materialCode 商品编码; materialId 为空时按其解析
     * @param lineNo       行号, 用于错误提示
     * @return 商品快照
     */
    TradeMaterialSnapshot resolve(Long materialId, String materialCode, int lineNo);
}
