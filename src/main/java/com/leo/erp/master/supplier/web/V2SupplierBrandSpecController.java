package com.leo.erp.master.supplier.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.master.supplier.service.SupplierBrandSpecService;
import com.leo.erp.master.supplier.web.dto.SupplierBrandSpecRequest;
import com.leo.erp.master.supplier.web.dto.SupplierBrandSpecResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供应商品牌规格配置接口: 维护「该供应商各经营品牌允许的 类别 + 材质 + 规格 + 定尺 组合」。
 *
 * <p><b>为什么是 {@code /suppliers/{id}/brand-specs} 而不是独立资源</b>: 配置没有独立的表头 —— 资源由
 * 路径里的供应商 id 唯一确定, 存储上是一组行, 对外建模成供应商的从属集合。因此用集合路径 + PUT 表达
 * 「整集替换」, 请求体不重复携带供应商 id(见仓库 RESTful 规范「仅从属资源使用嵌套路径, 最多一层」)。</p>
 *
 * <p><b>整集替换</b>: {@code PUT} 覆盖该供应商<b>全部品牌</b>的配置, 不是"按品牌逐个覆盖";
 * 请求体是配置数组本身(不是 {@code {items: [...]}} 包装对象), {@code []} 表示清空 = 回落到
 * 「按商品资料筛出的该品牌全量组合」。</p>
 *
 * <p><b>权限</b>: 读用 {@code suppliers:read}, 写用 {@code suppliers:update} —— 规格勾选是供应商资料的
 * 一部分, 不新增权限资源(否则前端权限目录要跟着迁移)。</p>
 *
 * <p><b>幂等</b>: 写接口沿用仓库统一门禁的类级 {@link IdempotencyRequired}, 客户端提交 PUT 需带
 * {@code Idempotency-Key}; 此外服务端对"内容完全相同的重复提交"不做任何行改写(见 service 注释)。</p>
 */
@Tag(name = "供应商品牌规格配置")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/suppliers/{id}/brand-specs")
public class V2SupplierBrandSpecController {

    private final SupplierBrandSpecService supplierBrandSpecService;

    public V2SupplierBrandSpecController(SupplierBrandSpecService supplierBrandSpecService) {
        this.supplierBrandSpecService = supplierBrandSpecService;
    }

    /**
     * 读取该供应商全部经营品牌的已配置组合。
     *
     * <p>未配置时返回空数组(不是 404): 空数组即「回落到按商品资料全量」。供应商不存在/已删除才 404。</p>
     */
    @Operation(summary = "查询供应商品牌规格配置",
            description = "返回该供应商全部未删除品牌的已配置组合, 顺序为 品牌/类别/材质/规格/定尺 升序;"
                    + " 未配置时返回空数组 []; 供应商不存在返回 404。")
    @GetMapping
    @RequirePermission(PermissionCodes.SUPPLIERS_READ)
    public List<SupplierBrandSpecResponse> list(
            @Parameter(description = "供应商 ID(雪花 ID, 十进制字符串)")
            @PathVariable @SnowflakeId Long id) {
        return supplierBrandSpecService.list(id);
    }

    /**
     * 整集替换该供应商全部品牌的配置。
     *
     * <p>成功返回 200 + 保存后的完整配置(与 GET 同一结构与排序)。空数组表示清空。校验失败 422:
     * 品牌不在该供应商经营品牌内、规格非正整数、类别/材质/定尺为空或超长、组合重复。</p>
     */
    @Operation(summary = "整集替换供应商品牌规格配置",
            description = "请求体是配置数组本身, 整体替换该供应商全部品牌的配置; 空数组 [] 表示清空(回落到按商品资料全量);"
                    + " 返回保存后的完整配置。校验不通过(品牌不在经营品牌内/规格非正整数/字段为空或超长/组合重复)返回 422。")
    @PutMapping
    @RequirePermission(PermissionCodes.SUPPLIERS_UPDATE)
    public List<SupplierBrandSpecResponse> replace(
            @Parameter(description = "供应商 ID(雪花 ID, 十进制字符串)")
            @PathVariable @SnowflakeId Long id,
            @RequestBody List<SupplierBrandSpecRequest> items) {
        return supplierBrandSpecService.replace(id, items);
    }
}
