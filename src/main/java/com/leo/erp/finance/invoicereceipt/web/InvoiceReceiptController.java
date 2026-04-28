package com.leo.erp.finance.invoicereceipt.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.invoicereceipt.service.InvoiceReceiptService;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;

@RestController
@RequestMapping("/invoice-receipts")
@Tag(name = "发票接收")
public class InvoiceReceiptController {

    private final InvoiceReceiptService service;

    public InvoiceReceiptController(InvoiceReceiptService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(resource = "invoice-receipt", action = "read")
    @Operation(summary = "分页查询发票接收")
    public ApiResponse<PageResponse<InvoiceReceiptResponse>> page(
            @BindPageQuery(sortFieldKey = "invoice-receipts") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(service.page(
                query,
                keyword,
                supplierName,
                status,
                startDate,
                endDate
        )));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "invoice-receipt", action = "read")
    @Operation(summary = "查询发票接收详情")
    public ApiResponse<InvoiceReceiptResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "invoice-receipt", action = "create")
    @Operation(summary = "创建发票接收")
    public ApiResponse<InvoiceReceiptResponse> create(@Valid @RequestBody InvoiceReceiptRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "invoice-receipt", action = "update")
    @Operation(summary = "更新发票接收")
    public ApiResponse<InvoiceReceiptResponse> update(@PathVariable Long id, @Valid @RequestBody InvoiceReceiptRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "invoice-receipt", action = "delete")
    @Operation(summary = "删除发票接收")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
