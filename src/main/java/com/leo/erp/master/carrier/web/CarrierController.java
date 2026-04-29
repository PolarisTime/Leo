package com.leo.erp.master.carrier.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.carrier.service.CarrierService;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/carriers")
public class CarrierController {

    private final CarrierService carrierService;

    public CarrierController(CarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @GetMapping("/options")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<java.util.List<com.leo.erp.common.web.OptionResponse>> options() {
        return ApiResponse.success(carrierService.listActiveOptions());
    }

    @GetMapping
    @RequiresPermission(resource = "carrier", action = "read")
    public ApiResponse<PageResponse<CarrierResponse>> page(
            @BindPageQuery(sortFieldKey = "carriers") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(carrierService.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "carrier", action = "read")
    public ApiResponse<CarrierResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(carrierService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "carrier", action = "create")
    public ApiResponse<CarrierResponse> create(@Valid @RequestBody CarrierRequest request) {
        return ApiResponse.success("创建成功", carrierService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "carrier", action = "update")
    public ApiResponse<CarrierResponse> update(@PathVariable Long id, @Valid @RequestBody CarrierRequest request) {
        return ApiResponse.success("更新成功", carrierService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "carrier", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        carrierService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
