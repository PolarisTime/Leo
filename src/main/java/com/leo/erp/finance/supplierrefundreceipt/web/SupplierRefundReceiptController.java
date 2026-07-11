package com.leo.erp.finance.supplierrefundreceipt.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.supplierrefundreceipt.service.SupplierRefundReceiptService;
import com.leo.erp.finance.supplierrefundreceipt.web.dto.SupplierRefundReceiptRequest;
import com.leo.erp.finance.supplierrefundreceipt.web.dto.SupplierRefundReceiptResponse;
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
import java.util.List;

@Tag(name = "供应商退款到账")
@RestController
@Validated
@RequestMapping("/supplier-refund-receipts")
public class SupplierRefundReceiptController {

    private final SupplierRefundReceiptService supplierRefundReceiptService;

    public SupplierRefundReceiptController(SupplierRefundReceiptService supplierRefundReceiptService) {
        this.supplierRefundReceiptService = supplierRefundReceiptService;
    }

    @Operation(summary = "搜索供应商退款到账单")
    @GetMapping("/search")
    @RequiresPermission(resource = "supplier-refund-receipt", action = "read")
    public ApiResponse<List<SupplierRefundReceiptResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(supplierRefundReceiptService.search(
                keyword == null ? "" : keyword,
                Math.min(limit, 500)
        ));
    }

    @Operation(summary = "分页查询供应商退款到账单")
    @GetMapping
    @RequiresPermission(resource = "supplier-refund-receipt", action = "read")
    public ApiResponse<PageResponse<SupplierRefundReceiptResponse>> page(
            @BindPageQuery(sortFieldKey = "supplier-refund-receipt") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(supplierRefundReceiptService.page(
                query,
                PageFilter.of(
                        keyword,
                        supplierName,
                        settlementCompanyId,
                        status,
                        startDate,
                        endDate
                )
        )));
    }

    @Operation(summary = "查询供应商退款到账单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "supplier-refund-receipt", action = "read")
    public ApiResponse<SupplierRefundReceiptResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(supplierRefundReceiptService.detail(id));
    }

    @Operation(summary = "创建供应商退款到账单")
    @PostMapping
    @RequiresPermission(resource = "supplier-refund-receipt", action = "create")
    public ApiResponse<SupplierRefundReceiptResponse> create(
            @Valid @RequestBody SupplierRefundReceiptRequest request
    ) {
        return ApiResponse.success("创建成功", supplierRefundReceiptService.create(request));
    }

    @Operation(summary = "更新供应商退款到账单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "supplier-refund-receipt", action = "update")
    public ApiResponse<SupplierRefundReceiptResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRefundReceiptRequest request
    ) {
        return ApiResponse.success("更新成功", supplierRefundReceiptService.update(id, request));
    }

    @Operation(summary = "更新供应商退款到账单状态")
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "supplier-refund-receipt", action = "audit")
    public ApiResponse<SupplierRefundReceiptResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return ApiResponse.success(
                "状态更新成功",
                supplierRefundReceiptService.updateStatus(id, request.status())
        );
    }

    @Operation(summary = "删除供应商退款到账单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "supplier-refund-receipt", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supplierRefundReceiptService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
