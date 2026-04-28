package com.leo.erp.finance.receipt.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.receipt.service.ReceiptService;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "收款管理")
@RestController
@RequestMapping("/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @Operation(summary = "分页查询收款单")
    @GetMapping
    @RequiresPermission(resource = "receipt", action = "read")
    public ApiResponse<PageResponse<ReceiptResponse>> page(
            @BindPageQuery(sortFieldKey = "receipts") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                receiptService.page(query, keyword, customerName, status, startDate, endDate)
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

    @Operation(summary = "删除收款单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "receipt", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        receiptService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
