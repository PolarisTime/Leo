package com.leo.erp.purchase.inbound.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.purchase.inbound.service.PurchaseInboundImportBatchService;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundImportBatchRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundImportBatchResponse;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundSplitPreviewResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "采购入库导入批次")
@RestController
public class PurchaseInboundImportBatchController {

    private final PurchaseInboundImportBatchService service;

    public PurchaseInboundImportBatchController(PurchaseInboundImportBatchService service) {
        this.service = service;
    }

    @GetMapping("/purchase-orders/{id}/inbound-split-preview")
    @PreAuthorize("@rbac.check('purchase-order', 'read')")
    @Operation(summary = "预览采购入库拆分结果")
    public ApiResponse<PurchaseInboundSplitPreviewResponse> preview(@PathVariable Long id) {
        return ApiResponse.success(service.preview(id));
    }

    @PostMapping("/purchase-orders/{id}/inbound-import-batches")
    @PreAuthorize("@rbac.check('purchase-inbound', 'create')")
    @Operation(summary = "原子创建采购入库拆分草稿")
    public ApiResponse<PurchaseInboundImportBatchResponse> create(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseInboundImportBatchRequest request
    ) {
        return ApiResponse.success("采购入库拆分草稿创建成功", service.create(id, request));
    }

    @GetMapping("/purchase-inbound-import-batches/{id}")
    @PreAuthorize("@rbac.check('purchase-inbound', 'read')")
    @Operation(summary = "查询采购入库导入批次")
    public ApiResponse<PurchaseInboundImportBatchResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }
}
