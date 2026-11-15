package com.leo.erp.market.supplierprice.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.api.Created;
import com.leo.erp.common.api.NoContent;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.market.supplierprice.service.SupplierPriceService;
import com.leo.erp.market.supplierprice.web.dto.SupplierPriceGridResponse;
import com.leo.erp.market.supplierprice.web.dto.SupplierPriceListRequests;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供应商价格表接口（比价一手价）。资源名与权限码资源 {@code supplier-price-lists} 对齐。
 *
 * <p><b>为什么读接口叫 {@code grid}</b>：界面是「一个品牌一张表、规格做行、供应商做列」，
 * 一次要拿到列集合与行集合才能渲染。这不是把 RPC 硬塞进 REST —— 它是该资源的<b>投影表示</b>：
 * {@code GET /supplier-price-lists/grid?brandName=中天} 读的仍是 supplier-price-list 这一族资源，
 * 只是按"品牌表"的形状一次性返回，避免前端为 N 个供应商各拉一次明细（实测 9 品牌 × 34 供应商
 * 会退化成 N+1）。</p>
 *
 * <p>写操作按"列"建模：一列 = 一条表头 + 该供应商的规格条目。</p>
 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/supplier-price-lists")
public class SupplierPriceListController {

    private final SupplierPriceService service;

    public SupplierPriceListController(SupplierPriceService service) {
        this.service = service;
    }

    /** 某品牌的整张价格表（列 + 行）。品牌必填：没有"全部品牌"的网格形态。 */
    @Operation(summary = "查询供应商价格表网格（品牌表：列 + 行）", description = "按品牌一次性返回该品牌下的供应商列与商品行投影，避免前端 N+1；brandName 必填，不分页。需 SUPPLIER_PRICE_LISTS_READ 权限。")
    @GetMapping("/grid")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public SupplierPriceGridResponse grid(@RequestParam String brandName) {
        return service.grid(brandName);
    }

    /**
     * 批量最低价：按 (品牌, 商品) 各取最低的一家。
     *
     * <p>报单比价一次要填满整张表（品牌列 × 商品行），逐格请求会变成几百次 —— 这是为那次填充
     * 提供的批量读。{@code brandName} 省略表示全部品牌。</p>
     */
    @Operation(summary = "批量查询各品牌最低报价", description = "按（品牌, 商品）各取最低一家供应商报价，供报单比价整表填充；省略 brandName 表示全部品牌，不分页。需 SUPPLIER_PRICE_LISTS_READ 权限。")
    @GetMapping("/lowest-quotes")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public List<SupplierPriceService.BrandQuote> lowestQuotes(
            @RequestParam(required = false) String brandName) {
        return service.lowestQuotes(brandName);
    }

    /** 增加一列：某品牌下接入一家供应商。 */
    @Operation(summary = "新增价格表列（接入供应商）", description = "在指定品牌下接入一家供应商，成功返回 201 与 Location /supplier-price-lists/{id}，响应体为该列（表头 + 规格条目，id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 SUPPLIER_PRICE_LISTS_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_CREATE)
    public ResponseEntity<SupplierPriceGridResponse.Column> addColumn(
            @Valid @RequestBody SupplierPriceListRequests.AddColumn request) {
        return ResponseSupport.created("/supplier-price-lists", service.addColumn(request));
    }

    /**
     * 去掉一列（软删除）。
     *
     * <p>删除的是「该供应商在这个品牌下的报价」，不影响其它品牌；条目随聚合根不再可见。</p>
     */
    @Operation(summary = "删除价格表列（软删）", description = "软删除某品牌下某供应商的整列报价，不影响其它品牌；成功返回 204 No Content，重复删除幂等。需 SUPPLIER_PRICE_LISTS_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_DELETE)
    public ResponseEntity<Void> removeColumn(@PathVariable @SnowflakeId Long id) {
        service.removeColumn(id);
        return ResponseSupport.noContent();
    }

    /**
     * 保存某一列的全部格子（整组替换）。
     *
     * <p>用 {@code PUT} 语义最贴合：提交的是这一列的<b>完整</b>内容，重复提交结果相同（幂等）。
     * 并发用请求体里的 {@code expectedVersion} 做前置校验，不符返回 409。</p>
     */
    @Operation(summary = "整组保存价格表列明细", description = "PUT 全量替换该列全部规格条目（重复提交结果一致）；请求体 expectedVersion 与库中版本不符返回 409。需 SUPPLIER_PRICE_LISTS_UPDATE 权限。")
    @PutMapping("/{id}/items")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_UPDATE)
    public SupplierPriceGridResponse.Column saveItems(
            @PathVariable @SnowflakeId Long id,
            @Valid @RequestBody SupplierPriceListRequests.SaveItems request) {
        return service.saveItems(id, request);
    }
}
