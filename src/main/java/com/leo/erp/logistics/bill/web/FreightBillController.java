package com.leo.erp.logistics.bill.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.logistics.bill.service.FreightBillService;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/freight-bills")
public class FreightBillController {

    private final FreightBillService service;

    public FreightBillController(FreightBillService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(resource = "freight-bill", action = "read")
    public ApiResponse<PageResponse<FreightBillResponse>> page(
            @BindPageQuery(sortFieldKey = "freight-bills") PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(service.page(query, keyword)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "freight-bill", action = "read")
    public ApiResponse<FreightBillResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "freight-bill", action = "create")
    public ApiResponse<FreightBillResponse> create(@Valid @RequestBody FreightBillRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "freight-bill", action = "update")
    public ApiResponse<FreightBillResponse> update(@PathVariable Long id, @Valid @RequestBody FreightBillRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "freight-bill", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
