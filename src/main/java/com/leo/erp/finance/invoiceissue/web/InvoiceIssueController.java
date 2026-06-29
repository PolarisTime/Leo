package com.leo.erp.finance.invoiceissue.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.invoiceissue.service.InvoiceIssueService;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import com.leo.erp.security.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@RestController
@Validated
@RequestMapping("/invoice-issues")
@Tag(name = "发票开具")
public class InvoiceIssueController {

    private final InvoiceIssueService service;

    public InvoiceIssueController(InvoiceIssueService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @RequiresPermission(resource = "invoice-issue", action = "read")
    @Operation(summary = "搜索发票开具")
    public ApiResponse<java.util.List<InvoiceIssueResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @GetMapping
    @RequiresPermission(resource = "invoice-issue", action = "read")
    @Operation(summary = "分页查询发票开具")
    public ApiResponse<PageResponse<InvoiceIssueResponse>> page(
            @BindPageQuery(sortFieldKey = "invoice-issue") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(service.page(
                query, PageFilter.of(keyword, customerName, settlementCompanyId, status, startDate, endDate)
        )));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "invoice-issue", action = "read")
    @Operation(summary = "查询发票开具详情")
    public ApiResponse<InvoiceIssueResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "invoice-issue", action = "create")
    @Operation(summary = "创建发票开具")
    public ApiResponse<InvoiceIssueResponse> create(@Valid @RequestBody InvoiceIssueRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "invoice-issue", action = "update")
    @Operation(summary = "更新发票开具")
    public ApiResponse<InvoiceIssueResponse> update(@PathVariable Long id, @Valid @RequestBody InvoiceIssueRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "invoice-issue", action = "audit")
    @Operation(summary = "更新发票开具状态")
    public ApiResponse<InvoiceIssueResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", service.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "invoice-issue", action = "delete")
    @Operation(summary = "删除发票开具")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }
}
