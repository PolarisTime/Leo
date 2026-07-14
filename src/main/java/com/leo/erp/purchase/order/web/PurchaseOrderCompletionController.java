package com.leo.erp.purchase.order.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.purchase.order.service.PurchaseOrderCompletionService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderCompletionResponse;
import com.leo.erp.security.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "采购订单")
@RestController
@RequestMapping("/purchase-orders")
public class PurchaseOrderCompletionController {

    private final PurchaseOrderCompletionService completionService;

    public PurchaseOrderCompletionController(PurchaseOrderCompletionService completionService) {
        this.completionService = completionService;
    }

    @Operation(summary = "完成采购")
    @PostMapping("/{id}/complete")
    @RequiresPermission(resource = "purchase-order", action = "audit")
    public ApiResponse<PurchaseOrderCompletionResponse> complete(@PathVariable Long id) {
        return ApiResponse.success("完成采购成功", completionService.completePurchaseOrder(id));
    }
}
