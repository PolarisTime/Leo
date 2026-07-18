package com.leo.erp.master.carrier.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.carrier.service.CarrierService;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/carriers")
public class CarrierController {

    private final CarrierService carrierService;

    public CarrierController(CarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @GetMapping("/options")
    public ApiResponse<List<CarrierOptionResponse>> options() {
        return ApiResponse.success(carrierService.listActiveOptions());
    }

    @GetMapping
    public ApiResponse<PageResponse<CarrierResponse>> page(
            @BindPageQuery(sortFieldKey = "carrier") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(carrierService.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    public ApiResponse<CarrierResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(carrierService.detail(id));
    }

    @PostMapping
    public ApiResponse<CarrierResponse> create(@Valid @RequestBody CarrierRequest request) {
        return ApiResponse.success("创建成功", carrierService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CarrierResponse> update(@PathVariable Long id, @Valid @RequestBody CarrierRequest request) {
        return ApiResponse.success("更新成功", carrierService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        carrierService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
