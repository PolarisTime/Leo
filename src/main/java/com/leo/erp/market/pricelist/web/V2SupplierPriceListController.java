package com.leo.erp.market.pricelist.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.pricelist.service.SupplierPriceListQueryService;
import com.leo.erp.market.pricelist.service.SupplierPriceListStore;
import com.leo.erp.market.pricelist.web.dto.MaterialSpecResponse;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentHistoryResponse;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentRequest;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentResponse;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceListRequest;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceListResponse;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceMatrixResponse;
import com.leo.erp.market.quotation.web.ResourceVersionPrecondition;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商价格表: 版本 CRUD、固定规格全集、对照矩阵、整表加减。
 *
 * <p>所有雪花 ID 通过 {@code JacksonConfig} 统一序列化为十进制字符串;
 * 读接口(规格全集/矩阵/详情)一律不写库。</p>
 */
@Tag(name = "供应商价格表")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/supplier-price-lists")
public class V2SupplierPriceListController {

    private static final String VERSION_HEADER = ResourceVersionPrecondition.HEADER;

    private final SupplierPriceListStore store;
    private final SupplierPriceListQueryService queryService;

    public V2SupplierPriceListController(SupplierPriceListStore store,
                                         SupplierPriceListQueryService queryService) {
        this.store = store;
        this.queryService = queryService;
    }

    @Operation(summary = "分页查询供应商价格表版本",
            description = "默认排序 released_at DESC, id DESC; 返回摘要(不含条目)。")
    @GetMapping
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public PageResponse<SupplierPriceListResponse.SummaryResponse> page(
            @BindPageQuery(sortFieldKey = "supplier-price-list") PageQuery query,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String brandName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime releasedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime releasedTo) {
        return PageResponse.from(store.page(query, supplierId, brandName, status, releasedFrom, releasedTo));
    }

    @Operation(summary = "固定规格全集",
            description = "来源 md_material(deleted_flag=false)去重, 规格归一化为整数; "
                    + "不含数字的规格(如 无/其他)整行跳过; 排序固定 category, material, spec_sort, length_sort。")
    @GetMapping("/spec-catalog")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public List<MaterialSpecResponse> specCatalog(@RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String material) {
        return queryService.specCatalog(category, material);
    }

    @Operation(summary = "对照矩阵投影(只读)",
            description = "每个 (供应商, 品牌) 取 asOf 之前 released_at 最大的未删除生效版本; "
                    + "asOf 缺省 = 当前时刻; GET 不写库。")
    @GetMapping("/matrix")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public SupplierPriceMatrixResponse matrix(
            @RequestParam(required = false) List<Long> supplierIds,
            @RequestParam(required = false) List<String> brandNames,
            @RequestParam(required = false) String category,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        return queryService.matrix(supplierIds, brandNames, category, asOf);
    }

    @Operation(summary = "价格表版本详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public SupplierPriceListResponse detail(@PathVariable Long id) {
        return store.detail(id);
    }

    @Operation(summary = "价格表条目列表")
    @GetMapping("/{id}/items")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public List<SupplierPriceListResponse.ItemResponse> items(@PathVariable Long id) {
        return store.items(id);
    }

    @Operation(summary = "创建价格表版本",
            description = "同 (supplierId, brandName) 已存在 released_at 更早的生效版本时自动归档旧版, "
                    + "响应返回 archivedListId; released_at 相同时 409。")
    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_CREATE)
    public ResponseEntity<SupplierPriceListResponse> create(
            @Valid @RequestBody SupplierPriceListRequest request) {
        return V2ResponseSupport.created("/supplier-price-lists", store.create(request));
    }

    @Operation(summary = "全量替换价格表版本",
            description = "全量替换表头与条目(幂等); ARCHIVED 版本不可改 → 409; "
                    + "可选携带 " + VERSION_HEADER + " 做乐观并发校验(不匹配 412)。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_UPDATE)
    public ResponseEntity<SupplierPriceListResponse> update(
            @PathVariable Long id,
            @Parameter(description = "资源版本(可选), 如 3")
            @RequestHeader(value = VERSION_HEADER, required = false) String resourceVersion,
            @Valid @RequestBody SupplierPriceListRequest request) {
        SupplierPriceListResponse response = store.update(id, request,
                ResourceVersionPrecondition.parse(resourceVersion, null, false));
        return withVersion(response, response.version());
    }

    @Operation(summary = "删除价格表版本", description = "软删除, 幂等返回 204。")
    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        store.delete(id);
        return V2ResponseSupport.noContent();
    }

    @Operation(summary = "整表/选区加减",
            description = "写 adjustment 头 + 明细留痕; itemIds 省略/空 = 整表; "
                    + "price 为 NULL(不报价)的条目不参与加减并计入 skippedCount; "
                    + "amount <= 0 或 mode 非法 → 422; 结果价为负 → 422(不静默截断)。")
    @PostMapping("/{id}/price-adjustments")
    @V2Created
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_UPDATE)
    public ResponseEntity<PriceAdjustmentResponse> adjust(
            @PathVariable Long id,
            @Valid @RequestBody PriceAdjustmentRequest request) {
        PriceAdjustmentResponse response = store.adjust(id, request, currentUserId(), currentUserName());
        return V2ResponseSupport.created("/supplier-price-lists/" + id + "/price-adjustments", response);
    }

    @Operation(summary = "价格表加减历史")
    @GetMapping("/{id}/price-adjustments")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public List<PriceAdjustmentHistoryResponse> adjustments(@PathVariable Long id) {
        return store.adjustments(id);
    }

    /** 当前操作人(与 JpaAuditConfig 同源): 未认证时回退 system。 */
    private static Long currentUserId() {
        return currentPrincipal().map(SecurityPrincipal::id).orElse(0L);
    }

    /** 当前操作人登录名, 未认证时回退 system。 */
    private static String currentUserName() {
        return currentPrincipal()
                .map(SecurityPrincipal::username)
                .filter(name -> name != null && !name.isBlank())
                .orElse("system");
    }

    private static java.util.Optional<SecurityPrincipal> currentPrincipal() {
        return java.util.Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(SecurityPrincipal.class::isInstance)
                .map(SecurityPrincipal.class::cast);
    }

    private static ResponseEntity<SupplierPriceListResponse> withVersion(SupplierPriceListResponse body,
                                                                        Long version) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (version != null) {
            builder.header(VERSION_HEADER, version.toString());
        }
        return builder.body(body);
    }

}
