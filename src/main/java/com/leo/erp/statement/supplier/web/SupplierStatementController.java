package com.leo.erp.statement.supplier.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.statement.supplier.service.SupplierStatementService;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementCandidateResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
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

@Tag(name = "供应商对账单")
@RestController
@Validated
@RequestMapping("/supplier-statements")
public class SupplierStatementController {

    private final SupplierStatementService supplierStatementService;

    public SupplierStatementController(SupplierStatementService supplierStatementService) {
        this.supplierStatementService = supplierStatementService;
    }

    @Operation(summary = "搜索供应商对账单")
    @GetMapping("/search")
    @RequiresPermission(resource = "supplier-statement", action = "read")
    public ApiResponse<java.util.List<SupplierStatementResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(
                supplierStatementService.search(keyword != null ? keyword : "", Math.min(limit, 500))
        );
    }

    @Operation(summary = "分页查询供应商对账单")
    @GetMapping
    @RequiresPermission(resource = "supplier-statement", action = "read")
    public ApiResponse<PageResponse<SupplierStatementResponse>> page(
            @BindPageQuery(sortFieldKey = "supplier-statement") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd
    ) {
        return ApiResponse.success(PageResponse.from(
                supplierStatementService.page(query, PageFilter.of(keyword, supplierName, status, periodStart, periodEnd))
        ));
    }

    @Operation(summary = "分页查询供应商对账单候选采购入库单")
    @GetMapping("/candidate")
    @RequiresPermission(resource = "supplier-statement", action = "read")
    public ApiResponse<PageResponse<SupplierStatementCandidateResponse>> candidates(
            @BindPageQuery(sortFieldKey = "purchase-inbound") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                supplierStatementService.candidatePage(query, PageFilter.of(keyword, supplierName, null, startDate, endDate))
        ));
    }

    @Operation(summary = "查询供应商对账单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "supplier-statement", action = "read")
    public ApiResponse<SupplierStatementResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(supplierStatementService.detail(id));
    }

    @Operation(summary = "创建供应商对账单")
    @PostMapping
    @RequiresPermission(resource = "supplier-statement", action = "create")
    public ApiResponse<SupplierStatementResponse> create(@Valid @RequestBody SupplierStatementRequest request) {
        return ApiResponse.success("创建成功", supplierStatementService.create(request));
    }

    @Operation(summary = "更新供应商对账单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "supplier-statement", action = "update")
    public ApiResponse<SupplierStatementResponse> update(@PathVariable Long id, @Valid @RequestBody SupplierStatementRequest request) {
        return ApiResponse.success("更新成功", supplierStatementService.update(id, request));
    }

    @Operation(summary = "更新供应商对账单状态")
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "supplier-statement", action = "audit")
    public ApiResponse<SupplierStatementResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", supplierStatementService.updateStatus(id, request.status()));
    }

    @Operation(summary = "删除供应商对账单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "supplier-statement", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supplierStatementService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
