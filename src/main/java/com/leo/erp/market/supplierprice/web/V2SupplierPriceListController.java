package com.leo.erp.market.supplierprice.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.market.supplierprice.service.SupplierPriceService;
import com.leo.erp.market.supplierprice.web.dto.SupplierPriceGridResponse;
import com.leo.erp.market.supplierprice.web.dto.SupplierPriceListRequests;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import jakarta.validation.Valid;
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
@RequestMapping(ApiVersion.V2_PREFIX + "/supplier-price-lists")
public class V2SupplierPriceListController {

    private final SupplierPriceService service;

    public V2SupplierPriceListController(SupplierPriceService service) {
        this.service = service;
    }

    /** 某品牌的整张价格表（列 + 行）。品牌必填：没有"全部品牌"的网格形态。 */
    @GetMapping("/grid")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public SupplierPriceGridResponse grid(@RequestParam String brandName) {
        return service.grid(brandName);
    }

    /** 增加一列：某品牌下接入一家供应商。 */
    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_CREATE)
    public ResponseEntity<SupplierPriceGridResponse.Column> addColumn(
            @Valid @RequestBody SupplierPriceListRequests.AddColumn request) {
        return V2ResponseSupport.created("/supplier-price-lists", service.addColumn(request));
    }

    /**
     * 去掉一列（软删除）。
     *
     * <p>删除的是「该供应商在这个品牌下的报价」，不影响其它品牌；条目随聚合根不再可见。</p>
     */
    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_DELETE)
    public ResponseEntity<Void> removeColumn(@PathVariable Long id) {
        service.removeColumn(id);
        return V2ResponseSupport.noContent();
    }

    /**
     * 保存某一列的全部格子（整组替换）。
     *
     * <p>用 {@code PUT} 语义最贴合：提交的是这一列的<b>完整</b>内容，重复提交结果相同（幂等）。
     * 并发用请求体里的 {@code expectedVersion} 做前置校验，不符返回 409。</p>
     */
    @PutMapping("/{id}/items")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_UPDATE)
    public SupplierPriceGridResponse.Column saveItems(
            @PathVariable Long id,
            @Valid @RequestBody SupplierPriceListRequests.SaveItems request) {
        return service.saveItems(id, request);
    }
}
