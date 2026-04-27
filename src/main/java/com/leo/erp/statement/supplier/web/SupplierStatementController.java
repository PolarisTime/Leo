package com.leo.erp.statement.supplier.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.statement.supplier.service.SupplierStatementService;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
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
@RequestMapping("/supplier-statements")
public class SupplierStatementController {

    private final SupplierStatementService supplierStatementService;

    public SupplierStatementController(SupplierStatementService supplierStatementService) {
        this.supplierStatementService = supplierStatementService;
    }

    @GetMapping
    @RequiresPermission(resource = "supplier-statement", action = "read")
    public ApiResponse<PageResponse<SupplierStatementResponse>> page(
            @BindPageQuery(sortFieldKey = "supplier-statements") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd
    ) {
        return ApiResponse.success(PageResponse.from(
                supplierStatementService.page(
                        query,
                        keyword,
                        supplierName,
                        status,
                        periodStart,
                        periodEnd
                )
        ));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "supplier-statement", action = "read")
    public ApiResponse<SupplierStatementResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(supplierStatementService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "supplier-statement", action = "create")
    public ApiResponse<SupplierStatementResponse> create(@Valid @RequestBody SupplierStatementRequest request) {
        return ApiResponse.success("创建成功", supplierStatementService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "supplier-statement", action = "update")
    public ApiResponse<SupplierStatementResponse> update(@PathVariable Long id, @Valid @RequestBody SupplierStatementRequest request) {
        return ApiResponse.success("更新成功", supplierStatementService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "supplier-statement", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supplierStatementService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
