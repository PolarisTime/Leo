package com.leo.erp.purchase.refund.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.refund.service.PurchaseRefundService;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundRequest;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundPreviewResponse;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundResponse;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundSourceCandidateResponse;
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

@Tag(name = "采购退款")
@RestController
@Validated
@RequestMapping("/purchase-refunds")
public class PurchaseRefundController {

    private final PurchaseRefundService purchaseRefundService;

    public PurchaseRefundController(PurchaseRefundService purchaseRefundService) {
        this.purchaseRefundService = purchaseRefundService;
    }

    @Operation(summary = "搜索采购退款单")
    @GetMapping("/search")
    @RequiresPermission(resource = "purchase-refund", action = "read")
    public ApiResponse<List<PurchaseRefundResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(purchaseRefundService.search(
                keyword == null ? "" : keyword,
                Math.min(limit, 500)
        ));
    }

    @Operation(summary = "预览采购退款差额")
    @GetMapping("/preview")
    @RequiresPermission(resource = "purchase-order", action = "read")
    public ApiResponse<PurchaseRefundPreviewResponse> preview(
            @RequestParam Long sourcePurchaseOrderId
    ) {
        return ApiResponse.success(purchaseRefundService.preview(sourcePurchaseOrderId));
    }

    @Operation(summary = "分页查询采购退款来源候选")
    @GetMapping("/source-candidates")
    @RequiresPermission(resource = "purchase-order", action = "read")
    public ApiResponse<PageResponse<PurchaseRefundSourceCandidateResponse>> sourceCandidates(
            @BindPageQuery(sortFieldKey = "purchase-order") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(purchaseRefundService.sourceCandidates(
                query,
                PageFilter.of(
                        keyword,
                        supplierName,
                        settlementCompanyId,
                        null,
                        startDate,
                        endDate
                ).withIdentity(null, null, supplierId, null, null)
        )));
    }

    @Operation(summary = "分页查询采购退款单")
    @GetMapping
    @RequiresPermission(resource = "purchase-refund", action = "read")
    public ApiResponse<PageResponse<PurchaseRefundResponse>> page(
            @BindPageQuery(sortFieldKey = "purchase-refund") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(purchaseRefundService.page(
                query,
                PageFilter.of(
                        keyword,
                        supplierName,
                        settlementCompanyId,
                        status,
                        startDate,
                        endDate
                ).withIdentity(null, null, supplierId, null, null)
        )));
    }

    @Operation(summary = "查询采购退款单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "purchase-refund", action = "read")
    public ApiResponse<PurchaseRefundResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(purchaseRefundService.detail(id));
    }

    @Operation(summary = "创建采购退款单")
    @PostMapping
    @RequiresPermission(resource = "purchase-refund", action = "create")
    public ApiResponse<PurchaseRefundResponse> create(@Valid @RequestBody PurchaseRefundRequest request) {
        return ApiResponse.success("创建成功", purchaseRefundService.create(request));
    }

    @Operation(summary = "更新采购退款单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "purchase-refund", action = "update")
    public ApiResponse<PurchaseRefundResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody PurchaseRefundRequest request) {
        return ApiResponse.success("更新成功", purchaseRefundService.update(id, request));
    }

    @Operation(summary = "更新采购退款单状态")
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "purchase-refund", action = "audit")
    public ApiResponse<PurchaseRefundResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return ApiResponse.success("状态更新成功", purchaseRefundService.updateStatus(id, request.status()));
    }

    @Operation(summary = "删除采购退款单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "purchase-refund", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        purchaseRefundService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
