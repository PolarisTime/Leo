package com.leo.erp.master.supplier.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.master.supplier.service.SupplierService;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
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
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping("/options")
    @RequiresPermission(resource = "supplier", action = "read")
    public ApiResponse<java.util.List<com.leo.erp.common.web.OptionResponse>> options() {
        return ApiResponse.success(supplierService.listActiveOptions());
    }

    @GetMapping
    @RequiresPermission(resource = "supplier", action = "read")
    public ApiResponse<PageResponse<SupplierResponse>> page(
            @BindPageQuery(sortFieldKey = "suppliers") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(supplierService.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "supplier", action = "read")
    public ApiResponse<SupplierResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(supplierService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "supplier", action = "create")
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return ApiResponse.success("创建成功", supplierService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "supplier", action = "update")
    public ApiResponse<SupplierResponse> update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return ApiResponse.success("更新成功", supplierService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "supplier", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
