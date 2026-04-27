package com.leo.erp.purchase.order.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    @RequiresPermission(resource = "purchase-order", action = "read")
    public ApiResponse<PageResponse<PurchaseOrderResponse>> page(
            @BindPageQuery(sortFieldKey = "purchase-orders") PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(purchaseOrderService.page(query, keyword)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "purchase-order", action = "read")
    public ApiResponse<PurchaseOrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(purchaseOrderService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "purchase-order", action = "create")
    public ApiResponse<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.success("创建成功", purchaseOrderService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "purchase-order", action = "update")
    public ApiResponse<PurchaseOrderResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.success("更新成功", purchaseOrderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "purchase-order", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        purchaseOrderService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
