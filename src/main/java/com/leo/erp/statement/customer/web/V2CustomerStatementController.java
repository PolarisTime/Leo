package com.leo.erp.statement.customer.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.StatusTransitionPermissionGuard;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.statement.customer.service.CustomerStatementService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementSummaryResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@Tag(name = "客户对账单")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/customer-statements")
public class V2CustomerStatementController {

    private final CustomerStatementService customerStatementService;

    public V2CustomerStatementController(CustomerStatementService customerStatementService) {
        this.customerStatementService = customerStatementService;
    }

    @Operation(summary = "分页查询客户对账单")
    @GetMapping
    @RequirePermission(PermissionCodes.CUSTOMER_STATEMENTS_READ)
    public PageResponse<CustomerStatementResponse> page(@BindPageQuery(sortFieldKey = "customer-statement") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId, @RequestParam(required = false) String customerName, @RequestParam(required = false) Long projectId, @RequestParam(required = false) String projectName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) String billDirection, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return PageResponse.from(customerStatementService.page(
                query,
                PageFilter.of(keyword, customerName, projectName, settlementCompanyId, status, periodStart, periodEnd)
                        .withIdentity(customerId, projectId, null, null, null),
                billDirection
        ));
    }

    @Operation(summary = "汇总客户对账单")
    @GetMapping("/summary")
    @RequirePermission(PermissionCodes.CUSTOMER_STATEMENTS_READ)
    public CustomerStatementSummaryResponse summary(@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId, @RequestParam(required = false) String customerName, @RequestParam(required = false) Long projectId, @RequestParam(required = false) String projectName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) String billDirection, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return customerStatementService.summary(
                PageFilter.of(keyword, customerName, projectName, settlementCompanyId, status, periodStart, periodEnd)
                        .withIdentity(customerId, projectId, null, null, null),
                billDirection
        );
    }

    @Operation(summary = "分页查询客户对账单候选销售订单")
    @GetMapping("/candidates")
    @RequirePermission(PermissionCodes.CUSTOMER_STATEMENTS_READ)
    public PageResponse<CustomerStatementCandidateResponse> candidates(@BindPageQuery(sortFieldKey = "sales-order") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId, @RequestParam(required = false) String customerName, @RequestParam(required = false) Long projectId, @RequestParam(required = false) String projectName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) Long currentStatementId) {
        return PageResponse.from(customerStatementService.candidatePage(
                query,
                PageFilter.of(keyword, customerName, projectName, settlementCompanyId, null, startDate, endDate)
                        .withIdentity(customerId, projectId, null, null, currentStatementId)
        ));
    }

    @Operation(summary = "查询客户对账单详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.CUSTOMER_STATEMENTS_READ)
    public CustomerStatementResponse detail(@PathVariable @SnowflakeId Long id) {
        return customerStatementService.detail(id);
    }

    @Operation(summary = "创建客户对账单")
    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.CUSTOMER_STATEMENTS_CREATE)
    public ResponseEntity<CustomerStatementResponse> create(@Valid @RequestBody CustomerStatementRequest request) {
        return V2ResponseSupport.created("/customer-statements", customerStatementService.create(request));
    }

    @Operation(summary = "更新客户对账单")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.CUSTOMER_STATEMENTS_UPDATE)
    public CustomerStatementResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody CustomerStatementRequest request) {
        return customerStatementService.update(id, request);
    }

    @Operation(summary = "更新客户对账单状态")
    @PatchMapping("/{id}/status")
    @RequirePermission(PermissionCodes.CUSTOMER_STATEMENTS_UPDATE)
    public CustomerStatementResponse updateStatus(@PathVariable @SnowflakeId Long id, @Valid @RequestBody StatusUpdateRequest request) {
        // 入口 fail-closed：目标态所需动作权限与 POST /confirmations 共用同一迁移表。
        StatusTransitionPermissionGuard.requireForTransitionAtEntry(ModuleKeys.CUSTOMER_STATEMENT, request.status());
        return customerStatementService.updateStatus(id, request.status());
    }

    /**
     * 确认动作端点：把客户对账单推进到已确认，返回确认后的父资源表示。
     * <p>这是状态迁移，不是创建资源——不返回 201，也不发 Location（无独立可回读的确认记录资源）；
     * 成功一律 200 + 最新对账单。反确认仍使用 {@code PATCH /customer-statements/{id}/status}。</p>
     */
    @Operation(summary = "确认客户对账单（状态迁移，200 + 最新对账单）")
    @IdempotencyRequired
    @PostMapping("/{id}/confirmations")
    @RequirePermission(PermissionCodes.CUSTOMER_STATEMENTS_CONFIRM)
    public ResponseEntity<CustomerStatementResponse> createConfirmation(@PathVariable @SnowflakeId Long id) {
        return ResponseEntity.ok(customerStatementService.updateStatus(id, StatusConstants.CONFIRMED));
    }

    @Operation(summary = "删除客户对账单")
    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.CUSTOMER_STATEMENTS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        customerStatementService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
