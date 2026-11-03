package com.leo.erp.master.supplier.web.dto;

/**
 * 一条「品牌 + 类别 + 材质 + 规格 + 定尺」配置(响应元素)。
 *
 * <p>GET 与 PUT 共用同一结构与同一排序(品牌、类别、材质、规格、定尺 升序), 前端可以按数组顺序直接渲染
 * antd Transfer 的右侧列表, 不需要再排一遍。</p>
 *
 * <p>不含任何 ID: 组合由五个字段自身定位, 前端只关心"哪些被勾选"; 供应商 id 来自请求路径。</p>
 */
public record SupplierBrandSpecResponse(

        /** 品牌名称(该供应商当前经营品牌之一)。 */
        String brandName,

        /** 类别。 */
        String category,

        /** 材质。 */
        String material,

        /** 规格 = 直径 mm。 */
        Integer spec,

        /** 定尺。 */
        String length) {
}
