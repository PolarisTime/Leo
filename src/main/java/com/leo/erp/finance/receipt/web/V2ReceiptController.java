package com.leo.erp.finance.receipt.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.ResourceVersionPrecondition;
import com.leo.erp.finance.receipt.ReceiptProperties;
import com.leo.erp.finance.receipt.service.ReceiptService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@Tag(name = "收款管理")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/receipts")
public class V2ReceiptController {

    private final ReceiptService receiptService;
    private final ReceiptProperties properties;

    public V2ReceiptController(ReceiptService receiptService, ReceiptProperties properties) {
        this.receiptService = receiptService;
        this.properties = properties;
    }

    @Operation(summary = "分页查询收款单")
    @GetMapping
    @RequirePermission(PermissionCodes.RECEIPTS_READ)
    public PageResponse<ReceiptResponse> page(@BindPageQuery(sortFieldKey = "receipt") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String customerName, @RequestParam(required = false) String counterpartyType, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(receiptService.page(
                query,
                PageFilter.of(keyword, customerName, settlementCompanyId, status, startDate, endDate)
                        .withBusinessType(counterpartyType)
        ));
    }

    @Operation(summary = "查询收款单详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.RECEIPTS_READ)
    public ReceiptResponse detail(@PathVariable @SnowflakeId Long id) {
        return receiptService.detail(id);
    }

    @Operation(summary = "创建收款单")
    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.RECEIPTS_CREATE)
    public ResponseEntity<ReceiptResponse> create(@Valid @RequestBody ReceiptRequest request) {
        return V2ResponseSupport.created("/receipts", receiptService.create(request));
    }

    @Operation(summary = "更新收款单",
            description = "整体替换收款单。写操作要求资源版本前置条件头 "
                    + ResourceVersionPrecondition.HEADER + "："
                    + "缺少版本返回 428，与单据当前版本不一致返回 412，一致才更新；"
                    + "成功返回 200 + 更新后的收款单表示（含新 version）。"
                    + "版本号即列表/详情响应里的 version 字段。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.RECEIPTS_UPDATE)
    public ReceiptResponse update(
            @PathVariable @SnowflakeId Long id,
            @Parameter(description = "资源版本(强比较), 取详情/列表响应的 version 字段")
            @RequestHeader(value = ResourceVersionPrecondition.HEADER, required = false) String resourceVersion,
            @Valid @RequestBody ReceiptRequest request) {
        return receiptService.update(id, request,
                ResourceVersionPrecondition.parse(resourceVersion, properties.isRequireResourceVersion()));
    }

    @Operation(summary = "更新收款单状态")
    @PatchMapping("/{id}/status")
    @RequirePermission(PermissionCodes.RECEIPTS_UPDATE)
    public ReceiptResponse updateStatus(@PathVariable @SnowflakeId Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return receiptService.updateStatus(id, request.status());
    }

    @Operation(summary = "删除收款单")
    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.RECEIPTS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        receiptService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
