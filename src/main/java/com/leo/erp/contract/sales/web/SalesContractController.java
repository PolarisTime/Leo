package com.leo.erp.contract.sales.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.contract.sales.service.SalesContractService;
import com.leo.erp.contract.sales.web.dto.SalesContractRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
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
@RequestMapping("/sales-contracts")
public class SalesContractController {

    private final SalesContractService salesContractService;

    public SalesContractController(SalesContractService salesContractService) {
        this.salesContractService = salesContractService;
    }

    @GetMapping
    @RequiresPermission(resource = "sales-contract", action = "read")
    public ApiResponse<PageResponse<SalesContractResponse>> page(
            @BindPageQuery(sortFieldKey = "sales-contracts") PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(salesContractService.page(query, keyword)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "sales-contract", action = "read")
    public ApiResponse<SalesContractResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(salesContractService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "sales-contract", action = "create")
    public ApiResponse<SalesContractResponse> create(@Valid @RequestBody SalesContractRequest request) {
        return ApiResponse.success("创建成功", salesContractService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "sales-contract", action = "update")
    public ApiResponse<SalesContractResponse> update(@PathVariable Long id, @Valid @RequestBody SalesContractRequest request) {
        return ApiResponse.success("更新成功", salesContractService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "sales-contract", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        salesContractService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
