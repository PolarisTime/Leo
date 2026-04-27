package com.leo.erp.contract.purchase.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.contract.purchase.service.PurchaseContractService;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/purchase-contracts")
public class PurchaseContractController {

    private final PurchaseContractService purchaseContractService;

    public PurchaseContractController(PurchaseContractService purchaseContractService) {
        this.purchaseContractService = purchaseContractService;
    }

    @GetMapping
    @RequiresPermission(resource = "purchase-contract", action = "read")
    public ApiResponse<PageResponse<PurchaseContractResponse>> page(
            @BindPageQuery(sortFieldKey = "purchase-contracts") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                purchaseContractService.page(query, keyword, supplierName, status, startDate, endDate)
        ));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "purchase-contract", action = "read")
    public ApiResponse<PurchaseContractResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(purchaseContractService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "purchase-contract", action = "create")
    public ApiResponse<PurchaseContractResponse> create(@Valid @RequestBody PurchaseContractRequest request) {
        return ApiResponse.success("创建成功", purchaseContractService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "purchase-contract", action = "update")
    public ApiResponse<PurchaseContractResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseContractRequest request) {
        return ApiResponse.success("更新成功", purchaseContractService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "purchase-contract", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        purchaseContractService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
