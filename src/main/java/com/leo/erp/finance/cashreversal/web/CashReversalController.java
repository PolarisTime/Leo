package com.leo.erp.finance.cashreversal.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.cashreversal.service.CashReversalService;
import com.leo.erp.finance.cashreversal.web.dto.CashReversalRequest;
import com.leo.erp.finance.cashreversal.web.dto.CashReversalResponse;
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

@Tag(name = "资金冲销管理")
@RestController
@Validated
@RequestMapping("/cash-reversals")
public class CashReversalController {

    private final CashReversalService service;

    public CashReversalController(CashReversalService service) {
        this.service = service;
    }

    @Operation(summary = "搜索资金冲销单")
    @GetMapping("/search")
    @RequiresPermission(resource = "cash-reversal", action = "read")
    public ApiResponse<java.util.List<CashReversalResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword == null ? "" : keyword, Math.min(limit, 500)));
    }

    @Operation(summary = "分页查询资金冲销单")
    @GetMapping
    @RequiresPermission(resource = "cash-reversal", action = "read")
    public ApiResponse<PageResponse<CashReversalResponse>> page(
            @BindPageQuery(sortFieldKey = "cash-reversal") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        PageFilter filter = PageFilter.of(
                keyword,
                null,
                settlementCompanyId,
                status,
                startDate,
                endDate
        );
        return ApiResponse.success(PageResponse.from(service.page(query, filter)));
    }

    @Operation(summary = "查询资金冲销单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "cash-reversal", action = "read")
    public ApiResponse<CashReversalResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @Operation(summary = "创建资金冲销单")
    @PostMapping
    @RequiresPermission(resource = "cash-reversal", action = "create")
    public ApiResponse<CashReversalResponse> create(@Valid @RequestBody CashReversalRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @Operation(summary = "更新资金冲销单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "cash-reversal", action = "update")
    public ApiResponse<CashReversalResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CashReversalRequest request
    ) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @Operation(summary = "审核资金冲销单")
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "cash-reversal", action = "audit")
    public ApiResponse<CashReversalResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return ApiResponse.success("状态更新成功", service.updateStatus(id, request.status()));
    }

    @Operation(summary = "删除资金冲销单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "cash-reversal", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }
}
