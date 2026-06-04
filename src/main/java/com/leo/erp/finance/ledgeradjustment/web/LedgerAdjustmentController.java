package com.leo.erp.finance.ledgeradjustment.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.ledgeradjustment.service.LedgerAdjustmentService;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import com.leo.erp.security.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "台账调整单")
@RestController
@Validated
@RequestMapping("/ledger-adjustments")
public class LedgerAdjustmentController {

    private final LedgerAdjustmentService ledgerAdjustmentService;

    public LedgerAdjustmentController(LedgerAdjustmentService ledgerAdjustmentService) {
        this.ledgerAdjustmentService = ledgerAdjustmentService;
    }

    @Operation(summary = "搜索台账调整单")
    @GetMapping("/search")
    @RequiresPermission(resource = "ledger-adjustment", action = "read")
    public ApiResponse<java.util.List<LedgerAdjustmentResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(ledgerAdjustmentService.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @Operation(summary = "分页查询台账调整单")
    @GetMapping
    @RequiresPermission(resource = "ledger-adjustment", action = "read")
    public ApiResponse<PageResponse<LedgerAdjustmentResponse>> page(
            @BindPageQuery(sortFieldKey = "ledger-adjustment") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String counterpartyType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                ledgerAdjustmentService.page(
                        query,
                        PageFilter.of(keyword, status, startDate, endDate),
                        direction,
                        counterpartyType
                )
        ));
    }

    @Operation(summary = "查询台账调整单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "ledger-adjustment", action = "read")
    public ApiResponse<LedgerAdjustmentResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(ledgerAdjustmentService.detail(id));
    }

    @Operation(summary = "创建台账调整单")
    @PostMapping
    @RequiresPermission(resource = "ledger-adjustment", action = "create")
    public ApiResponse<LedgerAdjustmentResponse> create(@Valid @RequestBody LedgerAdjustmentRequest request) {
        return ApiResponse.success("创建成功", ledgerAdjustmentService.create(request));
    }

    @Operation(summary = "更新台账调整单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "ledger-adjustment", action = "update")
    public ApiResponse<LedgerAdjustmentResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody LedgerAdjustmentRequest request) {
        return ApiResponse.success("更新成功", ledgerAdjustmentService.update(id, request));
    }

    @Operation(summary = "更新台账调整单状态")
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "ledger-adjustment", action = "audit")
    public ApiResponse<LedgerAdjustmentResponse> updateStatus(@PathVariable Long id,
                                                              @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", ledgerAdjustmentService.updateStatus(id, request.status()));
    }

    @Operation(summary = "删除台账调整单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "ledger-adjustment", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ledgerAdjustmentService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
