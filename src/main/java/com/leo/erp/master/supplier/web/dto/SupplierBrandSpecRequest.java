package com.leo.erp.master.supplier.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT {@code /suppliers/{id}/brand-specs} 请求体的一个元素: 一条「品牌 + 类别 + 材质 + 规格 + 定尺」组合。
 *
 * <p>请求体是<b>该记录的数组</b>(不是 {@code {items: [...]}} 包装对象): 资源整体由路径里的供应商 id 唯一
 * 确定, 数组即「整集」, 空数组表示清空配置(回落到按商品资料全量)。</p>
 *
 * <p>字段上的约束注解用于 OpenAPI 契约文档; 运行时校验以
 * {@code SupplierBrandSpecService#replace} 为准 —— 顶层数组的级联校验在 Spring MVC 里并不生效
 * ({@code @RequestBody} 绑定的是 {@code List} 本身, 不会级联到元素), 把校验放在 service 才能保证
 * 「不合法的元素一律 422、且一条都不落库」。两处规则必须保持一致。</p>
 *
 * <p>不含任何 ID: 供应商 id 取自路径, 品牌用名称定位(品牌本身不是主数据, 只有名称), 因此不存在
 * 雪花 ID 数值化的隐患。</p>
 */
public record SupplierBrandSpecRequest(

        /** 品牌名称; 必须是该供应商当前经营品牌之一(trim 后按名称精确匹配)。 */
        @NotBlank(message = "品牌不能为空")
        @Size(max = 64, message = "品牌名称长度不能超过64个字符")
        String brandName,

        /** 类别, 与商品资料 category 同口径。 */
        @NotBlank(message = "类别不能为空")
        @Size(max = 32, message = "类别长度不能超过32个字符")
        String category,

        /** 材质, 与商品资料 material 同口径(不含定尺)。 */
        @NotBlank(message = "材质不能为空")
        @Size(max = 32, message = "材质长度不能超过32个字符")
        String material,

        /** 规格 = 直径 mm, 必须为正整数。 */
        @NotNull(message = "规格不能为空")
        @Min(value = 1, message = "规格必须大于 0")
        Integer spec,

        /** 定尺, 与商品资料 length 同口径; 不允许为空(空定尺由商品资料侧的空串表达, 不在这里表达"不限制")。 */
        @NotBlank(message = "定尺不能为空")
        @Size(max = 16, message = "定尺长度不能超过16个字符")
        String length) {
}
