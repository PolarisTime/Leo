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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sales-contracts")
@Tag(name = "销售合同")
public class SalesContractController {

    private final SalesContractService salesContractService;

    public SalesContractController(SalesContractService salesContractService) {
        this.salesContractService = salesContractService;
    }

    @GetMapping
    @RequiresPermission(resource = "sales-contract", action = "read")
    @Operation(summary = "分页查询销售合同")
    public ApiResponse<PageResponse<SalesContractResponse>> page(
            @BindPageQuery(sortFieldKey = "sales-contracts") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                salesContractService.page(query, keyword, customerName, status, startDate, endDate)
        ));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "sales-contract", action = "read")
    @Operation(summary = "查询销售合同详情")
    public ApiResponse<SalesContractResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(salesContractService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "sales-contract", action = "create")
    @Operation(summary = "创建销售合同")
    public ApiResponse<SalesContractResponse> create(@Valid @RequestBody SalesContractRequest request) {
        return ApiResponse.success("创建成功", salesContractService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "sales-contract", action = "update")
    @Operation(summary = "更新销售合同")
    public ApiResponse<SalesContractResponse> update(@PathVariable Long id, @Valid @RequestBody SalesContractRequest request) {
        return ApiResponse.success("更新成功", salesContractService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "sales-contract", action = "delete")
    @Operation(summary = "删除销售合同")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        salesContractService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
