package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.material.service.MaterialCategoryService;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryRequest;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/material-categories")
public class MaterialCategoryController {

    private final MaterialCategoryService service;

    public MaterialCategoryController(MaterialCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<MaterialCategoryResponse>> page(
            @BindPageQuery PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(PageResponse.from(service.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaterialCategoryResponse> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    public ApiResponse<MaterialCategoryResponse> create(@Valid @RequestBody MaterialCategoryRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MaterialCategoryResponse> update(@PathVariable @Positive Long id,
                                                         @Valid @RequestBody MaterialCategoryRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/options")
    public ApiResponse<List<MaterialCategoryOptionResponse>> options() {
        return ApiResponse.success(service.options());
    }
}
