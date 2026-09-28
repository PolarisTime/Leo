package com.leo.erp.market.pricelist.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.pricelist.service.ValueAliasQuery;
import com.leo.erp.market.pricelist.service.ValueAliasStore;
import com.leo.erp.market.pricelist.web.dto.ValueAliasRequest;
import com.leo.erp.market.pricelist.web.dto.ValueAliasResolutionResponse;
import com.leo.erp.market.pricelist.web.dto.ValueAliasResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
 * 值映射/别名维护(类别 / 材质 / 定尺 / 品牌四个维度)。
 *
 * <p>把原先硬编码的归一规则(如 {@code 直条} ≡ {@code 螺纹钢})改为可维护数据,
 * 供规格键字典投影、价格表保存校验、比价推导匹配与品牌匹配共用同一条规则。</p>
 *
 * <p>状态码契约: 创建 201 + {@code Location}; 重复 (维度, 源值) 409;
 * 非法维度/空值/自映射 422; 资源不存在 404; 删除 204(软删, 幂等)。</p>
 */
@Tag(name = "值映射/别名")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/value-aliases")
public class V2ValueAliasController {

    private final ValueAliasStore store;
    private final ValueAliasQuery query;

    public V2ValueAliasController(ValueAliasStore store, ValueAliasQuery query) {
        this.store = store;
        this.query = query;
    }

    @Operation(summary = "分页查询值映射",
            description = "按 dimension(CATEGORY/MATERIAL/LENGTH/BRAND)与 keyword(源值/目标值模糊)筛选; "
                    + "排序 sortBy 仅支持 id/dimension/sourceValue/targetValue/createdAt/updatedAt, 缺省 id DESC。")
    @GetMapping
    @RequirePermission(PermissionCodes.VALUE_ALIASES_READ)
    public PageResponse<ValueAliasResponse> page(
            @BindPageQuery(sortFieldKey = "value-alias") PageQuery query,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String keyword) {
        return PageResponse.from(store.page(query, dimension, keyword));
    }

    @Operation(summary = "归一化预览(只读)",
            description = "回答\"这个写法会归一到什么\": 命中映射行返回目标写法(matched=true); "
                    + "未命中返回现有归一化回退结果(matched=false, 如 直条 → 螺纹钢)。不写库。")
    @GetMapping("/resolutions")
    @RequirePermission(PermissionCodes.VALUE_ALIASES_READ)
    public ValueAliasResolutionResponse resolve(@RequestParam String dimension,
                                                @RequestParam String value) {
        ValueAliasQuery.Resolution resolution = query.resolveByText(dimension, value);
        return new ValueAliasResolutionResponse(
                resolution.dimension() == null ? null : resolution.dimension().name(),
                resolution.value(),
                resolution.lookupKey(),
                resolution.targetValue(),
                resolution.matched());
    }

    @Operation(summary = "值映射详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.VALUE_ALIASES_READ)
    public ValueAliasResponse detail(@PathVariable @Positive Long id) {
        return store.detail(id);
    }

    @Operation(summary = "新增值映射",
            description = "同维度同源值的未删除记录唯一: 重复 → 409; 非法维度/空值/自映射 → 422。")
    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.VALUE_ALIASES_CREATE)
    public ResponseEntity<ValueAliasResponse> create(@Valid @RequestBody ValueAliasRequest request) {
        return V2ResponseSupport.created("/value-aliases", store.create(request));
    }

    @Operation(summary = "编辑值映射",
            description = "全量替换维度/源值/目标值/备注; 目标键与其它记录冲突 → 409; 自映射 → 422; 不存在 → 404。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.VALUE_ALIASES_UPDATE)
    public ValueAliasResponse update(@PathVariable @Positive Long id,
                                     @Valid @RequestBody ValueAliasRequest request) {
        return store.update(id, request);
    }

    @Operation(summary = "删除值映射",
            description = "软删除, 返回 204 且幂等; 已软删记录重复删除同样 204, 记录从未存在才是 404; "
                    + "软删后同 (维度, 源值) 可重新创建。")
    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.VALUE_ALIASES_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        store.delete(id);
        return V2ResponseSupport.noContent();
    }
}
