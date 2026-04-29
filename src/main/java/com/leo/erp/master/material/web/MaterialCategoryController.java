package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.master.material.service.MaterialCategoryService;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryRequest;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import com.leo.erp.security.permission.RequiresPermission;
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
    private final MaterialCategoryRepository repository;

    public MaterialCategoryController(MaterialCategoryService service, MaterialCategoryRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @GetMapping
    @RequiresPermission(resource = "material", action = "read")
    public ApiResponse<PageResponse<MaterialCategoryResponse>> page(
            @BindPageQuery PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(PageResponse.from(service.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "material", action = "read")
    public ApiResponse<MaterialCategoryResponse> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "material", action = "create")
    public ApiResponse<MaterialCategoryResponse> create(@Valid @RequestBody MaterialCategoryRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "material", action = "update")
    public ApiResponse<MaterialCategoryResponse> update(@PathVariable @Positive Long id,
                                                         @Valid @RequestBody MaterialCategoryRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "material", action = "delete")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/options")
    @RequiresPermission(resource = "material", action = "read")
    public ApiResponse<List<MaterialCategoryOptionResponse>> options() {
        List<MaterialCategoryOptionResponse> options = repository
                .findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常")
                .stream()
                .map(cat -> new MaterialCategoryOptionResponse(
                        cat.getCategoryName(),
                        cat.getCategoryName(),
                        Boolean.TRUE.equals(cat.getPurchaseWeighRequired())
                ))
                .toList();
        return ApiResponse.success(options);
    }
}
