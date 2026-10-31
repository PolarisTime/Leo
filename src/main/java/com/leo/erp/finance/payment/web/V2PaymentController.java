package com.leo.erp.finance.payment.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.ResourceVersionPrecondition;
import com.leo.erp.finance.payment.PaymentProperties;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
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

@Tag(name = "付款管理")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/payments")
public class V2PaymentController {

    private final PaymentService paymentService;
    private final PaymentProperties properties;

    public V2PaymentController(PaymentService paymentService, PaymentProperties properties) {
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @Operation(summary = "分页查询付款单")
    @GetMapping
    @RequirePermission(PermissionCodes.PAYMENTS_READ)
    public PageResponse<PaymentResponse> page(@BindPageQuery(sortFieldKey = "payment") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String businessType, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(paymentService.page(
                query,
                new PageFilter(keyword, status, startDate, endDate,
                        null, null, businessType, null, null, null, null, null, null, null)
        ));
    }

    @Operation(summary = "查询付款单详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.PAYMENTS_READ)
    public PaymentResponse detail(@PathVariable @SnowflakeId Long id) {
        return paymentService.detail(id);
    }

    @Operation(summary = "创建付款单")
    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.PAYMENTS_CREATE)
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return V2ResponseSupport.created("/payments", paymentService.create(request));
    }

    @Operation(summary = "更新付款单",
            description = "整体替换付款单。写操作要求资源版本前置条件头 "
                    + ResourceVersionPrecondition.HEADER + "（兼容别名 If-Match）："
                    + "缺少版本返回 428，与单据当前版本不一致返回 412，一致才更新；"
                    + "成功返回 200 + 更新后的付款单表示（含新 version）。"
                    + "版本号即列表/详情响应里的 version 字段。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.PAYMENTS_UPDATE)
    public PaymentResponse update(
            @PathVariable @SnowflakeId Long id,
            @Parameter(description = "资源版本(强比较), 取详情/列表响应的 version 字段")
            @RequestHeader(value = ResourceVersionPrecondition.HEADER, required = false) String resourceVersion,
            @Parameter(description = "兼容别名, 非标准弱验证器用法")
            @RequestHeader(value = ResourceVersionPrecondition.LEGACY_HEADER, required = false) String ifMatch,
            @Valid @RequestBody PaymentRequest request) {
        return paymentService.update(id, request,
                ResourceVersionPrecondition.parse(resourceVersion, ifMatch, properties.isRequireResourceVersion()));
    }

    @Operation(summary = "更新付款单状态")
    @PatchMapping("/{id}/status")
    @RequirePermission(PermissionCodes.PAYMENTS_UPDATE)
    public PaymentResponse updateStatus(@PathVariable @SnowflakeId Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return paymentService.updateStatus(id, request.status());
    }

    @Operation(summary = "删除付款单")
    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.PAYMENTS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        paymentService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
