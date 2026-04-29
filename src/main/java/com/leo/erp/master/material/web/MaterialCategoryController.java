package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/materials")
public class MaterialCategoryController {

    private final MaterialCategoryRepository repository;

    public MaterialCategoryController(MaterialCategoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/categories")
    public ApiResponse<List<MaterialCategoryOptionResponse>> categories() {
        List<MaterialCategoryOptionResponse> options = repository
                .findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常")
                .stream()
                .map(cat -> new MaterialCategoryOptionResponse(cat.getCategoryName(), cat.getCategoryName()))
                .toList();
        return ApiResponse.success(options);
    }
}
