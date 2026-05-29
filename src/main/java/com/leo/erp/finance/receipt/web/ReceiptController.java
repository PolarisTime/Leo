package com.leo.erp.finance.receipt.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.receipt.service.ReceiptService;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
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

@Tag(name = "收款管理")
@RestController
@Validated
@RequestMapping("/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @Operation(summary = "搜索收款单")
    @GetMapping("/search")
    @RequiresPermission(resource = "receipt", action = "read")
    public ApiResponse<java.util.List<ReceiptResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(receiptService.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @Operation(summary = "分页查询收款单")
    @GetMapping
    @RequiresPermission(resource = "receipt", action = "read")
    public ApiResponse<PageResponse<ReceiptResponse>> page(
            @BindPageQuery(sortFieldKey = "receipt") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                receiptService.page(query, PageFilter.of(keyword, customerName, status, startDate, endDate))
        ));
    }

    @Operation(summary = "查询收款单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "receipt", action = "read")
    public ApiResponse<ReceiptResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(receiptService.detail(id));
    }

    @Operation(summary = "创建收款单")
    @PostMapping
    @RequiresPermission(resource = "receipt", action = "create")
    public ApiResponse<ReceiptResponse> create(@Valid @RequestBody ReceiptRequest request) {
        return ApiResponse.success("创建成功", receiptService.create(request));
    }

    @Operation(summary = "更新收款单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "receipt", action = "update")
    public ApiResponse<ReceiptResponse> update(@PathVariable Long id, @Valid @RequestBody ReceiptRequest request) {
        return ApiResponse.success("更新成功", receiptService.update(id, request));
    }

    @Operation(summary = "更新收款单状态")
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "receipt", action = "audit")
    public ApiResponse<ReceiptResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", receiptService.updateStatus(id, request.status()));
    }

    @Operation(summary = "删除收款单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "receipt", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        receiptService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
