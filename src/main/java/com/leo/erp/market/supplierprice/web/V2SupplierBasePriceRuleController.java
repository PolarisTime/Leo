package com.leo.erp.market.supplierprice.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.web.ResourceVersionPrecondition;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.market.supplierprice.SupplierPriceProperties;
import com.leo.erp.market.supplierprice.service.SupplierBasePriceRuleService;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleRequest;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供应商品牌基价加价规则接口: 按 {@code (supplierId, brandName)} 维护「基价 + 各规格加减」。
 *
 * <p><b>为什么 PUT 落在集合路径而不是 {@code /{id}}</b>: 这套规则没有独立的表头 id —— 资源由
 * {@code (supplierId, brandName)} 这一对业务键唯一确定, 存储上则是若干行。对外把它建模成「一个由业务键
 * 定位的整体资源」, 用集合路径 + 请求体里的业务键表达 PUT(整体替换), 比伪造一个表头 id 更贴近真实模型;
 * 请求体不重复携带路径 id 的约束由业务键在请求体里唯一承担。并发语义与仓库其它报价资源一致, 走
 * {@code X-Resource-Version} 前置条件头(412/428)。</p>
 *
 * <p>权限码复用 {@code supplier-price-lists:read/update}: 基价规则是该价格表录入的辅助口径, 不新增
 * 权限资源(否则前端权限目录要跟着迁移)。</p>
 */
@Tag(name = "供应商品牌基价加价规则")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/supplier-base-price-rules")
public class V2SupplierBasePriceRuleController {

    private static final String VERSION_HEADER = ResourceVersionPrecondition.HEADER;
    private static final String VERSION_ALIAS_NOTE =
            "也接受兼容别名 If-Match(非标准: 数据库版本号是弱验证器, 不符合 RFC 9110 强比较要求)。";

    private final SupplierBasePriceRuleService service;
    private final SupplierPriceProperties properties;

    public V2SupplierBasePriceRuleController(SupplierBasePriceRuleService service,
                                             SupplierPriceProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /** 读取某供应商某品牌的整套规则; 未配置时返回 version=0 的空结构。 */
    @Operation(summary = "查询供应商品牌基价加价规则",
            description = "按 (supplierId, brandName) 返回整套规则; 未配置时 base 为 null、items 为空、version 为 0。")
    @GetMapping
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public SupplierBasePriceRuleResponse find(
            @Parameter(description = "供应商 ID(雪花 ID, 十进制字符串)")
            @RequestParam @SnowflakeId Long supplierId,
            @Parameter(description = "品牌/产地")
            @RequestParam @NotBlank @Size(max = 64) String brandName) {
        return service.find(supplierId, brandName);
    }

    /**
     * 整组替换某供应商某品牌的规则。
     *
     * <p>请求头 {@code X-Resource-Version} 是版本前置条件(强比较); 缺头且配置要求时 428, 版本不匹配 412;
     * 响应头回传保存后的最新版本。{@code items} 为空表示清空这套规则(整体替换为空), 版本仍 +1;
     * {@code base} 非空时必须出现在 {@code items} 中且 delta 为 0。</p>
     */
    @Operation(summary = "整组替换供应商品牌基价加价规则",
            description = "整体替换(先软删旧行再写入新行, 同一事务); items 为空表示清空该套规则, 版本号仍 +1;"
                    + " 要求资源版本前置条件头 " + VERSION_HEADER + ", " + VERSION_ALIAS_NOTE
                    + " 缺少版本返回 428, 版本不匹配返回 412; 响应头回传最新版本。")
    @PutMapping
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_UPDATE)
    public ResponseEntity<SupplierBasePriceRuleResponse> save(
            @Parameter(description = "资源版本(强比较), 如 3")
            @RequestHeader(value = VERSION_HEADER, required = false) String resourceVersion,
            @Parameter(description = "兼容别名, 非标准弱验证器用法")
            @RequestHeader(value = ResourceVersionPrecondition.LEGACY_HEADER, required = false) String ifMatch,
            @Valid @RequestBody SupplierBasePriceRuleRequest request) {
        SupplierBasePriceRuleResponse response = service.save(request,
                ResourceVersionPrecondition.parse(resourceVersion, ifMatch,
                        properties.isRequireResourceVersion()));
        return ResponseEntity.ok()
                .header(VERSION_HEADER, String.valueOf(response.version()))
                .body(response);
    }
}
