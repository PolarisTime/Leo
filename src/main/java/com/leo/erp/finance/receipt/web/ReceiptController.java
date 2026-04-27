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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping
    @RequiresPermission(resource = "receipt", action = "read")
    public ApiResponse<PageResponse<ReceiptResponse>> page(
            @BindPageQuery(sortFieldKey = "receipts") PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(receiptService.page(query, keyword)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "receipt", action = "read")
    public ApiResponse<ReceiptResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(receiptService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "receipt", action = "create")
    public ApiResponse<ReceiptResponse> create(@Valid @RequestBody ReceiptRequest request) {
        return ApiResponse.success("创建成功", receiptService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "receipt", action = "update")
    public ApiResponse<ReceiptResponse> update(@PathVariable Long id, @Valid @RequestBody ReceiptRequest request) {
        return ApiResponse.success("更新成功", receiptService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "receipt", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        receiptService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
