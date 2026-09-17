package com.leo.erp.sales.contract.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.ResourceVersionPrecondition;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.contract.SalesContractProperties;
import com.leo.erp.sales.contract.service.SalesContractService;
import com.leo.erp.sales.contract.web.dto.SalesContractRequest;
import com.leo.erp.sales.contract.web.dto.SalesContractResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "销售合同")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/sales-contracts")
public class V2SalesContractController {

    private static final String VERSION_HEADER = ResourceVersionPrecondition.HEADER;

    private final SalesContractService service;
    private final SalesContractProperties properties;

    public V2SalesContractController(SalesContractService service, SalesContractProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Operation(summary = "分页查询销售合同")
    @GetMapping
    @RequirePermission(PermissionCodes.SALES_CONTRACTS_READ)
    public PageResponse<SalesContractResponse> page(
            @BindPageQuery(sortFieldKey = "sales-contract") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return PageResponse.from(service.page(query, keyword, customerId, projectId, status));
    }

    @Operation(summary = "销售合同详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.SALES_CONTRACTS_READ)
    public SalesContractResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @Operation(summary = "创建销售合同")
    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.SALES_CONTRACTS_CREATE)
    public ResponseEntity<SalesContractResponse> create(@Valid @RequestBody SalesContractRequest request) {
        return V2ResponseSupport.created("/sales-contracts", service.create(request));
    }

    @Operation(summary = "整体替换销售合同",
            description = "要求资源版本前置条件头 " + VERSION_HEADER + "(兼容别名 If-Match); "
                    + "该前置条件是否必需由 leo.sales.contract.require-resource-version 控制, "
                    + "开启时缺少版本返回 428, 版本不匹配返回 412; 响应头回传最新版本。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.SALES_CONTRACTS_UPDATE)
    public ResponseEntity<SalesContractResponse> update(
            @PathVariable Long id,
            @Parameter(description = "资源版本(强比较), 如 3")
            @RequestHeader(value = VERSION_HEADER, required = false) String resourceVersion,
            @Parameter(description = "兼容别名, 非标准弱验证器用法")
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody SalesContractRequest request) {
        SalesContractResponse response = service.update(id, request,
                ResourceVersionPrecondition.parse(resourceVersion, ifMatch,
                        properties.isRequireResourceVersion()));
        return withVersion(response, response.version());
    }

    @Operation(summary = "删除销售合同(软删)")
    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.SALES_CONTRACTS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return V2ResponseSupport.noContent();
    }

    @Operation(summary = "变更销售合同状态",
            description = "状态机: 草稿→已审核→已发出→归档, 不支持逆向回退; "
                    + "作废仅允许自 草稿/已审核/归档, 已发出须先归档; 非法流转返回 422。")
    @PatchMapping("/{id}/status")
    @RequirePermission(PermissionCodes.SALES_CONTRACTS_UPDATE)
    public ResponseEntity<SalesContractResponse> updateStatus(@PathVariable Long id,
                                                              @Valid @RequestBody StatusUpdateRequest request) {
        SalesContractResponse response = service.updateStatus(id, request.status());
        return withVersion(response, response.version());
    }

    private static <T> ResponseEntity<T> withVersion(T body, Long version) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (version != null) {
            builder.header(VERSION_HEADER, version.toString());
        }
        return builder.body(body);
    }
}
